package bio.terra.cbas.controllers;

import static bio.terra.cbas.common.MetricsUtil.recordRecordsInRequest;
import static bio.terra.cbas.common.MetricsUtil.recordRunsSubmittedPerRunSet;
import static bio.terra.cbas.model.RunSetState.ERROR;
import static bio.terra.cbas.model.RunSetState.RUNNING;
import static bio.terra.cbas.models.CbasRunStatus.SYSTEM_ERROR;
import static bio.terra.cbas.models.CbasRunStatus.UNKNOWN;

import bio.terra.cbas.api.RunSetsApi;
import bio.terra.cbas.config.CbasApiConfiguration;
import bio.terra.cbas.dao.MethodDao;
import bio.terra.cbas.dao.RunDao;
import bio.terra.cbas.dao.RunSetDao;
import bio.terra.cbas.dependencies.wds.WdsService;
import bio.terra.cbas.dependencies.wes.CromwellService;
import bio.terra.cbas.model.RunSetRequest;
import bio.terra.cbas.model.RunSetState;
import bio.terra.cbas.model.RunSetStateResponse;
import bio.terra.cbas.model.RunState;
import bio.terra.cbas.model.RunStateResponse;
import bio.terra.cbas.models.CbasRunStatus;
import bio.terra.cbas.models.Method;
import bio.terra.cbas.models.Run;
import bio.terra.cbas.models.RunSet;
import bio.terra.cbas.runsets.inputs.InputGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import cromwell.client.model.RunId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.databiosphere.workspacedata.client.ApiException;
import org.databiosphere.workspacedata.model.ErrorResponse;
import org.databiosphere.workspacedata.model.RecordResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class RunSetsApiController implements RunSetsApi {

  private final CromwellService cromwellService;
  private final WdsService wdsService;
  private final MethodDao methodDao;
  private final RunSetDao runSetDao;
  private final RunDao runDao;
  private final ObjectMapper objectMapper;
  private final CbasApiConfiguration cbasApiConfiguration;

  private record WdsRecordResponseDetails(
      ArrayList<RecordResponse> recordResponseList, Map<String, String> recordIdsWithError) {}

  public RunSetsApiController(
      CromwellService cromwellService,
      WdsService wdsService,
      ObjectMapper objectMapper,
      MethodDao methodDao,
      RunDao runDao,
      RunSetDao runSetDao,
      CbasApiConfiguration cbasApiConfiguration) {
    this.cromwellService = cromwellService;
    this.wdsService = wdsService;
    this.objectMapper = objectMapper;
    this.methodDao = methodDao;
    this.runSetDao = runSetDao;
    this.runDao = runDao;
    this.cbasApiConfiguration = cbasApiConfiguration;
  }

  @Override
  public ResponseEntity<RunSetStateResponse> postRunSet(RunSetRequest request) {
    // request validation
    List<String> requestErrors = validateRequest(request, this.cbasApiConfiguration);
    if (!requestErrors.isEmpty()) {
      String errorMsg = "Bad user request. Error(s): " + requestErrors;
      log.warn(errorMsg);
      return new ResponseEntity<>(
          new RunSetStateResponse().errors(errorMsg), HttpStatus.BAD_REQUEST);
    }

    // Fetch WDS Records and keep track of errors while retrieving records
    WdsRecordResponseDetails wdsRecordResponses = fetchWdsRecords(request);
    if (wdsRecordResponses.recordIdsWithError.size() > 0) {
      String errorMsg =
          "Error while fetching WDS Records for Record ID(s): "
              + wdsRecordResponses.recordIdsWithError;
      log.warn(errorMsg);
      return new ResponseEntity<>(
          new RunSetStateResponse().errors(errorMsg), HttpStatus.BAD_REQUEST);
    }

    // Store new method
    UUID methodId = UUID.randomUUID();
    Method method;
    try {
      method =
          new Method(
              methodId,
              request.getWorkflowUrl(),
              objectMapper.writeValueAsString(request.getWorkflowInputDefinitions()),
              objectMapper.writeValueAsString(request.getWorkflowOutputDefinitions()),
              request.getWdsRecords().getRecordType());
      methodDao.createMethod(method);
    } catch (JsonProcessingException e) {
      log.warn("Failed to record method to database", e);
      return new ResponseEntity<>(
          new RunSetStateResponse()
              .errors("Failed to record method to database. Error(s): " + e.getMessage()),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Create a new run_set
    UUID runSetId = UUID.randomUUID();
    RunSet runSet = new RunSet(runSetId, method);
    runSetDao.createRunSet(runSet);

    // For each Record ID, build workflow inputs and submit the workflow to Cromwell
    List<RunStateResponse> runStateResponseList =
        buildInputsAndSubmitRun(request, runSet, wdsRecordResponses.recordResponseList);

    // Figure out how many runs are in Failed state. If all Runs are in an Error state then mark the
    // Run Set as Failed
    RunSetState runSetState;
    List<RunStateResponse> runsInErrorState =
        runStateResponseList.stream()
            .filter(run -> CbasRunStatus.fromValue(run.getState()).inErrorState())
            .toList();

    if (runsInErrorState.size() == request.getWdsRecords().getRecordIds().size()) {
      runSetState = ERROR;
    } else runSetState = RUNNING;

    // Return the result
    return new ResponseEntity<>(
        new RunSetStateResponse()
            .runSetId(runSetId.toString())
            .runs(runStateResponseList)
            .state(runSetState),
        HttpStatus.OK);
  }

  public static List<String> validateRequest(RunSetRequest request, CbasApiConfiguration config) {
    List<String> errorList = new ArrayList<>();

    // check number of Record IDs in request is within allowed limit
    int recordIdsSize = request.getWdsRecords().getRecordIds().size();
    int recordIdsMax = config.getRunSetsMaximumRecordIds();
    if (recordIdsSize > recordIdsMax) {
      errorList.add(
          "%s record IDs submitted exceeds the maximum value of %s."
              .formatted(recordIdsSize, recordIdsMax));
    }

    // check that there are no duplicated Record IDs present in the request
    List<String> recordIds = request.getWdsRecords().getRecordIds();
    List<String> duplicateRecordIds =
        recordIds.stream().filter(e -> Collections.frequency(recordIds, e) > 1).distinct().toList();
    if (duplicateRecordIds.size() > 0) {
      errorList.add("Duplicate Record ID(s) %s present in request.".formatted(duplicateRecordIds));
    }

    return errorList;
  }

  private String getErrorMessage(ApiException exception) {
    Gson gson = new Gson();
    try {
      ErrorResponse error = gson.fromJson(exception.getResponseBody(), ErrorResponse.class);
      if (error != null) {
        return error.getMessage();
      } else {
        return exception.getMessage();
      }
    } catch (Exception e) {
      return exception.getMessage();
    }
  }

  private WdsRecordResponseDetails fetchWdsRecords(RunSetRequest request) {
    String recordType = request.getWdsRecords().getRecordType();
    List<String> recordIds = request.getWdsRecords().getRecordIds();
    recordRecordsInRequest(recordIds.size());

    ArrayList<RecordResponse> recordResponses = new ArrayList<>();
    HashMap<String, String> recordIdsWithError = new HashMap<>();
    for (String recordId : recordIds) {
      try {
        recordResponses.add(wdsService.getRecord(recordType, recordId));
      } catch (ApiException e) {
        log.warn("Record lookup for Record ID {} failed.", recordId, e);
        recordIdsWithError.put(recordId, getErrorMessage(e));
      }
    }

    return new WdsRecordResponseDetails(recordResponses, recordIdsWithError);
  }

  private RunStateResponse storeRun(
      UUID runId,
      String externalId,
      RunSet runSet,
      String recordId,
      CbasRunStatus runState,
      String errors) {
    String additionalErrorMsg = "";
    int created =
        runDao.createRun(
            new Run(
                runId,
                externalId,
                runSet,
                recordId,
                OffsetDateTime.now(),
                runState,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                errors));

    if (created != 1) {
      additionalErrorMsg =
          String.format(
              "CBAS failed to create new row for Record ID %s in %s state in database. INSERT returned '%s rows created'",
              recordId, runState, created);
      log.error(additionalErrorMsg);
    }

    return new RunStateResponse()
        .runId(runId.toString())
        .state(CbasRunStatus.toCbasApiState(runState))
        .errors(errors + additionalErrorMsg);
  }

  private List<RunStateResponse> buildInputsAndSubmitRun(
      RunSetRequest request, RunSet runSet, ArrayList<RecordResponse> recordResponses) {
    ArrayList<RunStateResponse> runStateResponseList = new ArrayList<>();

    for (RecordResponse record : recordResponses) {
      // Build the inputs set from workflow parameter definitions and the fetched record
      Map<String, Object> workflowInputs =
          InputGenerator.buildInputs(request.getWorkflowInputDefinitions(), record);

      // Submit the workflow, get its ID and store the Run to database
      RunId workflowResponse;
      UUID runId = UUID.randomUUID();
      try {
        workflowResponse = cromwellService.submitWorkflow(request.getWorkflowUrl(), workflowInputs);
        runStateResponseList.add(
            storeRun(runId, workflowResponse.getRunId(), runSet, record.getId(), UNKNOWN, null));
      } catch (cromwell.client.ApiException e) {
        String errorMsg =
            String.format(
                "Cromwell submission failed for Record ID %s. ApiException: ", record.getId());
        log.warn(errorMsg, e);
        runStateResponseList.add(
            storeRun(runId, null, runSet, record.getId(), SYSTEM_ERROR, errorMsg + e.getMessage()));
      } catch (JsonProcessingException e) {
        // Should be super rare that jackson cannot convert an object to Json...
        String errorMsg =
            String.format(
                "Failed to convert inputs object to JSON for Record ID %s.", record.getId());
        log.warn(errorMsg, e);
        runStateResponseList.add(
            storeRun(runId, null, runSet, record.getId(), SYSTEM_ERROR, errorMsg + e.getMessage()));
      }
    }

    long successfulRuns =
        runStateResponseList.stream().filter(r -> r.getState() == RunState.UNKNOWN).count();
    recordRunsSubmittedPerRunSet(successfulRuns);

    return runStateResponseList;
  }
}
