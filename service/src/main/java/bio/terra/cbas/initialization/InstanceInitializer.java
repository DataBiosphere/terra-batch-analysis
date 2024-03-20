package bio.terra.cbas.initialization;

import bio.terra.cbas.util.BackfillOriginalWorkspaceIdService;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class InstanceInitializer implements ApplicationListener<ContextRefreshedEvent> {

  private final BackfillOriginalWorkspaceIdService backfillOriginalWorkspaceIdService;

  public InstanceInitializer(
      BackfillOriginalWorkspaceIdService backfillOriginalWorkspaceIdService) {
    this.backfillOriginalWorkspaceIdService = backfillOriginalWorkspaceIdService;
  }

  @Override
  public void onApplicationEvent(@NotNull ContextRefreshedEvent event) {
    backfillOriginalWorkspaceIdService.backfillAll();
  }
}