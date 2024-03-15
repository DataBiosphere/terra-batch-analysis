package bio.terra.cbas.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import bio.terra.cbas.config.CbasContextConfiguration;
import bio.terra.cbas.dao.MethodDao;
import bio.terra.cbas.dao.MethodVersionDao;
import bio.terra.cbas.dao.RunDao;
import bio.terra.cbas.dao.RunSetDao;
import bio.terra.cbas.models.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"})
@Testcontainers
public class TestCloneRecoveryBean {
  @Autowired MethodDao methodDao;
  @Autowired RunSetDao runSetDao;
  @Autowired RunDao runDao;
  @Autowired MethodVersionDao methodVersionDao;
  @Mock CbasContextConfiguration cbasContextConfig;

  @Container
  static JdbcDatabaseContainer postgres =
      new PostgreSQLContainer("postgres:14")
          .withDatabaseName("test_db")
          .withUsername("test_user")
          .withPassword("test_password");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.jdbc-url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @BeforeAll
  static void setup() {
    postgres.start();
  }

  @BeforeEach
  void init() {
    when(cbasContextConfig.getWorkspaceId()).thenReturn(currentWorkspaceId);
    when(cbasContextConfig.getWorkspaceCreatedDate()).thenReturn(currentWorkspaceCreatedDate);
  }

  @AfterEach
  void cleanupDb() throws SQLException {
    DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .createStatement()
        .execute("DELETE FROM run_set; DELETE FROM method_version; DELETE FROM method;");
  }

  @Test
  void testRecoveryFromWorkspaceCloning() {
    methodDao.createMethod(clonedMethod);
    methodVersionDao.createMethodVersion(clonedMethodVersion);
    runSetDao.createRunSet(clonedTemplate);
    runSetDao.createRunSet(clonedRunSet);
    runDao.createRun(clonedRun);

    CloneRecoveryBean cloneRecoveryBean =
        new CloneRecoveryBean(runSetDao, runDao, methodDao, methodVersionDao, cbasContextConfig);

    cloneRecoveryBean.pruneCloneSourceWorkspaceHistory();

    List<RunSet> remainingRunSets = runSetDao.getRunSetsWithMethodId(clonedMethod.methodId());
    List<Run> remainingRuns = runDao.getRuns(new RunDao.RunsFilters(clonedRunSet.runSetId(), null));
    RunSet remainingRunSet = remainingRunSets.get(0);

    assertEquals(1, remainingRunSets.size());
    assertEquals(0, remainingRuns.size());
    assertEquals(true, remainingRunSet.isTemplate());
    assertEquals(clonedRunSet.runSetId(), remainingRunSet.runSetId());
  }

  @Test
  void testRecoveryFromAppUpgrade() {
    CloneRecoveryBean cloneRecoveryBean =
        new CloneRecoveryBean(runSetDao, runDao, methodDao, methodVersionDao, cbasContextConfig);

    cloneRecoveryBean.pruneCloneSourceWorkspaceHistory();
  }

  private final UUID originalWorkspaceId = UUID.randomUUID();
  private final UUID currentWorkspaceId = UUID.randomUUID();
  private final OffsetDateTime currentWorkspaceCreatedDate =
      OffsetDateTime.parse("2023-01-27T19:21:24.563932Z");

  Method clonedMethod =
      new Method(
          UUID.randomUUID(),
          "",
          "",
          OffsetDateTime.parse("2023-01-27T19:21:24.563932Z"),
          null,
          "",
          originalWorkspaceId);

  MethodVersion clonedMethodVersion =
      new MethodVersion(
          UUID.randomUUID(),
          clonedMethod,
          "",
          "",
          OffsetDateTime.parse("2023-01-27T19:21:24.563932Z"),
          null,
          "",
          originalWorkspaceId,
          "");

  RunSet clonedTemplate =
      new RunSet(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          clonedMethodVersion,
          "",
          "",
          false,
          true,
          CbasRunSetStatus.COMPLETE,
          OffsetDateTime.parse("2023-01-27T19:21:24.563932Z"),
          OffsetDateTime.parse("2023-01-27T19:21:24.563932Z"),
          OffsetDateTime.parse("2023-01-27T19:21:24.563932Z"),
          0,
          0,
          "[]",
          "[]",
          "",
          "",
          originalWorkspaceId);

  RunSet clonedRunSet =
      new RunSet(
          UUID.fromString("00000000-0000-0000-0000-000000000002"),
          clonedMethodVersion,
          "",
          "",
          false,
          false,
          CbasRunSetStatus.COMPLETE,
          OffsetDateTime.parse("2023-01-28T19:21:24.563932Z"),
          OffsetDateTime.parse("2023-01-28T19:21:24.563932Z"),
          OffsetDateTime.parse("2023-01-28T19:21:24.563932Z"),
          0,
          0,
          "[]",
          "[]",
          "",
          "",
          originalWorkspaceId);

  Run clonedRun =
      new Run(
          UUID.randomUUID(),
          UUID.randomUUID().toString(),
          clonedRunSet,
          "",
          clonedRunSet.submissionTimestamp(),
          CbasRunStatus.COMPLETE,
          clonedRunSet.lastModifiedTimestamp(),
          clonedRunSet.lastPolledTimestamp(),
          "");
}
