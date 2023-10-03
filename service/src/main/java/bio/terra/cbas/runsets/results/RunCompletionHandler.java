package bio.terra.cbas.runsets.results;

import static bio.terra.cbas.common.MetricsUtil.recordMethodCompletion;

import bio.terra.cbas.common.DateUtils;
import bio.terra.cbas.common.exceptions.OutputProcessingException;
import bio.terra.cbas.dao.RunDao;
import bio.terra.cbas.dependencies.wds.WdsService;
import bio.terra.cbas.dependencies.wds.WdsServiceException;
import bio.terra.cbas.model.WorkflowOutputDefinition;
import bio.terra.cbas.models.CbasRunStatus;
import bio.terra.cbas.models.Run;
import bio.terra.cbas.runsets.outputs.OutputGenerator;
import bio.terra.cbas.runsets.types.CoercionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.databiosphere.workspacedata.model.RecordAttributes;
import org.databiosphere.workspacedata.model.RecordRequest;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RunCompletionHandler {
  private final RunDao runDao;
  private final WdsService wdsService;
  private final ObjectMapper objectMapper;
  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(RunCompletionHandler.class);

  public RunCompletionHandler(RunDao runDao, WdsService wdsService, ObjectMapper objectMapper) {
    this.runDao = runDao;
    this.wdsService = wdsService;
    this.objectMapper = objectMapper;
  }

  /*
  The copy of SmartRunsPoller code.
  Refactoring SmartRunsPoller will be covered in [WM-2090].
   */
  public boolean hasOutputDefinition(Run run) throws JsonProcessingException {
    List<WorkflowOutputDefinition> outputDefinitionList =
        objectMapper.readValue(run.runSet().outputDefinition(), new TypeReference<>() {});
    return !outputDefinitionList.isEmpty();
  }

  /*
  The copy of SmartRunsPoller code.
  Refactoring SmartRunsPoller will be covered in [WM-2090].
   */
  public void updateOutputAttributes(Run run, Object outputs)
      throws JsonProcessingException, WdsServiceException, CoercionException,
          OutputProcessingException {

    List<WorkflowOutputDefinition> outputDefinitionList =
        objectMapper.readValue(run.runSet().outputDefinition(), new TypeReference<>() {});
    RecordAttributes outputParamDef = OutputGenerator.buildOutputs(outputDefinitionList, outputs);
    RecordRequest request = new RecordRequest().attributes(outputParamDef);

    logger.info(
        "Updating output attributes for Record ID {} from Run {}.", run.recordId(), run.engineId());

    wdsService.updateRecord(request, run.runSet().recordType(), run.recordId());
  }

  @SuppressWarnings("unchecked")
  public RunCompletionResult updateResults(
      Run updatableRun, CbasRunStatus status, Object workflowOutputs, List<String> workflowErrors) {
    long updateResultsStartNanos = System.nanoTime();
    RunCompletionResult updateResult = RunCompletionResult.ERROR;
    try {
      var updatedRunState = status;

      if (updatableRun.status() == updatedRunState && workflowOutputs == null) {
        // Status is already up-to-date, there are no outputs to save.
        return updateDatabaseRunStatusOnly(updatableRun);
      }

      // Pull workflow completion information and save run outputs
      ArrayList<String> errors = new ArrayList<>();

      // Saving run outputs for a Successful terminal status only.
      if (updatedRunState == CbasRunStatus.COMPLETE
          && workflowOutputs != null
          && !((Map<String, Object>) workflowOutputs).isEmpty()) {
        // Assuming that if no outputs were not passed with results,
        // Then no explicit pull should be made for outputs from Cromwell.
        String errorMessage;

        try {
          // we only write back output attributes to WDS when output definition is not empty.
          if (hasOutputDefinition(updatableRun)) {
            updateOutputAttributes(updatableRun, workflowOutputs);
          }
        } catch (OutputProcessingException | JsonProcessingException | CoercionException e) {
          // log error and return validation exception in case
          // the json schema of output is not as expected.
          errorMessage =
              "Error while processing workflow output attributes for record %s from run %s (engine workflow ID %s): %s"
                  .formatted(
                      updatableRun.recordId(),
                      updatableRun.runId(),
                      updatableRun.engineId(),
                      e.getMessage());
          logger.error(errorMessage, e);
          // This error is not retryable, therefore return a validation error result.
          return RunCompletionResult.VALIDATION;
        } catch (Exception e) {
          // log WDS or other Runtime error and mark Run as Failed.
          errorMessage =
              "Error while updating data table attributes for record %s from run %s (engine workflow ID %s): %s"
                  .formatted(
                      updatableRun.recordId(),
                      updatableRun.runId(),
                      updatableRun.engineId(),
                      e.getMessage());
          logger.error(errorMessage, e);
          errors.add(errorMessage);
        }

        if (!errors.isEmpty()) {
          // If this is the last attempt to save workflow outputs,
          // update run info in database with an error.
          // Otherwise, we should return internal error to caller.
          updatedRunState = CbasRunStatus.SYSTEM_ERROR;
        }
      } else if (updatedRunState.inErrorState()) {
        if (workflowErrors != null && !workflowErrors.isEmpty()) {
          errors.addAll(workflowErrors);
        }
      }

      // Save the updated run record in database.
      updateResult = updateDatabaseRunStatus(updatableRun, updatedRunState, errors);
    } finally {
      recordMethodCompletion(updateResultsStartNanos, RunCompletionResult.SUCCESS == updateResult);
    }
    // we should not come here
    return updateResult;
  }

  private RunCompletionResult updateDatabaseRunStatusOnly(Run updatableRun) {
    // Only update last polled timestamp.
    logger.info(
        "Update last modified timestamp for Run {} (engine ID {}) in status {}.",
        updatableRun.runId(),
        updatableRun.engineId(),
        updatableRun.status());

    var changes = runDao.updateLastPolledTimestamp(updatableRun.runId());
    if (changes != 1) {
      logger.warn(
          "Expected 1 row change updating last_polled_timestamp for Run {} in status {}, but got {}.",
          updatableRun.runId(),
          updatableRun.status(),
          changes);
      return RunCompletionResult.ERROR;
    }
    return RunCompletionResult.SUCCESS;
  }

  private RunCompletionResult updateDatabaseRunStatus(
      Run updatableRun, CbasRunStatus updatedRunState, ArrayList<String> errors) {
    OffsetDateTime engineChangedTimestamp = DateUtils.currentTimeInUTC();
    int changes;

    if (errors.isEmpty()) {
      logger.info(
          "Updating status of Run {} (engine ID {}) from {} to {} with no errors",
          updatableRun.runId(),
          updatableRun.engineId(),
          updatableRun.status(),
          updatedRunState);
      changes =
          runDao.updateRunStatus(updatableRun.runId(), updatedRunState, engineChangedTimestamp);
    } else {
      logger.info(
          "Updating status of Run {} (engine ID {}) from {} to {} with {} errors",
          updatableRun.runId(),
          updatableRun.engineId(),
          updatableRun.status(),
          updatedRunState,
          errors.size());
      updatableRun = updatableRun.withErrorMessages(String.join(", ", errors));
      changes =
          runDao.updateRunStatusWithError(
              updatableRun.runId(),
              updatedRunState,
              engineChangedTimestamp,
              updatableRun.errorMessages());
    }
    if (changes == 1) {
      return RunCompletionResult.SUCCESS;
    }

    String databaseUpdateErrorMessage =
        "Run %s was attempted to update status from %s to %s but no DB rows were changed by the query."
            .formatted(updatableRun.runId(), updatableRun.status(), updatedRunState);
    logger.warn(databaseUpdateErrorMessage);
    return RunCompletionResult.ERROR;
  }
}
