package bio.terra.cbas.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.cbas.models.Method;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestMethodDao {

  @Autowired MethodDao methodDao;
  UUID methodId = UUID.fromString("00000000-0000-0000-0000-000000000005");

  @Test
  void retrievesSingleMethod() {
    Method testMethod =
        new Method(
            methodId,
            "assemble_refbased",
            "assemble_refbased",
            OffsetDateTime.parse("2023-01-27T19:21:24.542692Z"),
            null,
            "Github");

    Method actual = methodDao.getMethod(methodId);

    /*
    Asserting each column value separately here and omitting the 'created' column due to github
    passing in a current_timestamp() value, causing the test to fail.
    */

    assertEquals(testMethod.method_id(), actual.method_id());
    assertEquals(testMethod.name(), actual.name());
    assertEquals(testMethod.description(), actual.description());
    assertEquals(testMethod.lastRunSetId(), actual.lastRunSetId());
    assertEquals(testMethod.methodSource(), actual.methodSource());
  }

  @Test
  void retrievesAllMethods() {

    List<Method> allMethods = new ArrayList<>();

    allMethods.add(
        new Method(
            UUID.fromString("00000000-0000-0000-0000-000000000005"),
            "assemble_refbased",
            "assemble_refbased",
            OffsetDateTime.parse("2023-01-27T19:21:24.542692Z"),
            null,
            "Github"));
    allMethods.add(
        new Method(
            UUID.fromString("00000000-0000-0000-0000-000000000006"),
            "sarscov2_nextstrain",
            "sarscov2_nextstrain",
            OffsetDateTime.parse("2023-01-27T19:21:24.552878Z"),
            null,
            "Github"));
    allMethods.add(
        new Method(
            UUID.fromString("00000000-0000-0000-0000-000000000008"),
            "fetch_sra_to_bam",
            "fetch_sra_to_bam",
            OffsetDateTime.parse("2023-01-27T19:21:24.563932Z"),
            null,
            "Github"));

    List<Method> actual = methodDao.getMethods();

    // Assertions for assemble_refbased
    assertEquals(allMethods.get(0).method_id(), actual.get(0).method_id());
    assertEquals(allMethods.get(0).name(), actual.get(0).name());
    assertEquals(allMethods.get(0).description(), actual.get(0).description());
    assertEquals(allMethods.get(0).lastRunSetId(), actual.get(0).lastRunSetId());
    assertEquals(allMethods.get(0).methodSource(), actual.get(0).methodSource());

    // Assertions for sarscov2_nextstrain
    assertEquals(allMethods.get(1).method_id(), actual.get(1).method_id());
    assertEquals(allMethods.get(1).name(), actual.get(1).name());
    assertEquals(allMethods.get(1).description(), actual.get(1).description());
    assertEquals(allMethods.get(1).lastRunSetId(), actual.get(1).lastRunSetId());
    assertEquals(allMethods.get(1).methodSource(), actual.get(1).methodSource());

    // Assertions for fetch_sra_to_bam
    assertEquals(allMethods.get(2).method_id(), actual.get(2).method_id());
    assertEquals(allMethods.get(2).name(), actual.get(2).name());
    assertEquals(allMethods.get(2).description(), actual.get(2).description());
    assertEquals(allMethods.get(2).lastRunSetId(), actual.get(2).lastRunSetId());
    assertEquals(allMethods.get(2).methodSource(), actual.get(2).methodSource());

    assertEquals(3, actual.size());
  }
}
