package bio.terra.cbas.initialization;

import bio.terra.cbas.config.CbasContextConfiguration;
import bio.terra.cbas.dao.MethodDao;
import bio.terra.cbas.dao.MethodVersionDao;
import bio.terra.cbas.dao.RunSetDao;
import bio.terra.cbas.util.BackfillOriginalWorkspaceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackfillOriginalWorkspaceIdBean {
  private final Logger logger = LoggerFactory.getLogger(BackfillOriginalWorkspaceIdBean.class);
  private final RunSetDao runSetDao;
  private final MethodDao methodDao;
  private final MethodVersionDao methodVersionDao;
  private final CbasContextConfiguration cbasContextConfig;

  public BackfillOriginalWorkspaceIdBean(
      RunSetDao runSetDao,
      MethodDao methodDao,
      MethodVersionDao methodVersionDao,
      CbasContextConfiguration cbasContextConfig) {
    this.runSetDao = runSetDao;
    this.methodDao = methodDao;
    this.methodVersionDao = methodVersionDao;
    this.cbasContextConfig = cbasContextConfig;
  }

  public void backfillOriginalWorkspaceIds() {
    Boolean workspaceCreatedDateIsValid = false;

    try {
      // call this without assignment to make sure it's not empty,
      // e.g. during local testing, or due to an upstream error
      cbasContextConfig.getWorkspaceCreatedDate();
      workspaceCreatedDateIsValid = true;
    } catch (java.time.format.DateTimeParseException e) {
      logger.error(
          "Aborting backfillOriginalWorkspaceIds; There was an error parsing workspaceCreatedDate: {}",
          e.getMessage());
    }

    if (workspaceCreatedDateIsValid) {
      logger.info(
          "Backfilling original workspace IDs (workspaceId: {}, workspaceCreatedDate: {}",
          cbasContextConfig.getWorkspaceId(),
          cbasContextConfig.getWorkspaceCreatedDate());

      // NOTE:  The following BackfillOriginalWorkspaceIds method calls are inherently temporary.
      //        Once all original workspace IDs have been backfilled, these lines
      //        (and the BackfillOriginalWorkspaceIds class itself) should be deleted.
      BackfillOriginalWorkspaceIds.backfillRunSets(runSetDao, cbasContextConfig, logger);
      BackfillOriginalWorkspaceIds.backfillMethods(methodDao, cbasContextConfig, logger);
      BackfillOriginalWorkspaceIds.backfillMethodVersions(
          methodVersionDao, cbasContextConfig, logger);
    }
  }
}
