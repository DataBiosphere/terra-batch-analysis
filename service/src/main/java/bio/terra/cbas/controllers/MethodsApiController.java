package bio.terra.cbas.controllers;

import bio.terra.cbas.api.MethodsApi;
import bio.terra.cbas.common.DateUtils;
import bio.terra.cbas.dao.MethodDao;
import bio.terra.cbas.dao.MethodVersionDao;
import bio.terra.cbas.model.MethodDetails;
import bio.terra.cbas.model.MethodListResponse;
import bio.terra.cbas.model.MethodVersionDetails;
import bio.terra.cbas.models.Method;
import bio.terra.cbas.models.MethodVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class MethodsApiController implements MethodsApi {

  private final MethodDao methodDao;
  private final MethodVersionDao methodVersionDao;

  public MethodsApiController(MethodDao methodDao, MethodVersionDao methodVersionDao) {
    this.methodDao = methodDao;
    this.methodVersionDao = methodVersionDao;
  }

  @Override
  public ResponseEntity<MethodListResponse> getMethods(Boolean showVersions, UUID methodVersionId) {

    if (methodVersionId != null) {
      return ResponseEntity.ok(
          new MethodListResponse()
              .methods(
                  List.of(
                      methodVersionToMethodDetails(
                          methodVersionDao.getMethodVersion(methodVersionId)))));
    } else {
      boolean nullSafeShowVersions = showVersions == null || showVersions;
      return ResponseEntity.ok(
          new MethodListResponse()
              .methods(
                  methodDao.getMethods().stream()
                      .map(m -> methodToMethodDetails(m, nullSafeShowVersions))
                      .toList()));
    }
  }

  private MethodDetails methodToMethodDetails(Method method, boolean includeVersions) {

    List<MethodVersionDetails> versions =
        includeVersions
            ? methodVersionDao.getMethodVersionsForMethod(method).stream()
                .map(MethodsApiController::methodVersionToMethodVersionDetails)
                .toList()
            : null;

    return new MethodDetails()
        .methodId(method.method_id())
        .name(method.name())
        .description(method.description())
        .source(method.methodSource())
        .created(DateUtils.convertToDate(method.created()))
        .lastRun(DateUtils.convertToDate(method.lastRun()))
        .methodVersions(versions);
  }

  private static MethodVersionDetails methodVersionToMethodVersionDetails(
      MethodVersion methodVersion) {
    return new MethodVersionDetails()
        .methodVersionId(methodVersion.methodVersionId())
        .methodId(methodVersion.method().method_id())
        .name(methodVersion.name())
        .description(methodVersion.description())
        .created(DateUtils.convertToDate(methodVersion.created()))
        .lastRun(DateUtils.convertToDate(methodVersion.lastRun()))
        .url(methodVersion.url());
  }

  private MethodDetails methodVersionToMethodDetails(MethodVersion methodVersion) {
    Method method = methodVersion.method();
    return new MethodDetails()
        .methodId(method.method_id())
        .name(method.name())
        .description(method.description())
        .source(method.methodSource())
        .created(DateUtils.convertToDate(method.created()))
        .lastRun(DateUtils.convertToDate(method.lastRun()))
        .methodVersions(List.of(methodVersionToMethodVersionDetails(methodVersion)));
  }
}
