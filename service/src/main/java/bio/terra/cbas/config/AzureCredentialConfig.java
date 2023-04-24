package bio.terra.cbas.config;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "credentials.azure")
public class AzureCredentialConfig {

  private Duration tokenAcquisitionTimeout;
  private Duration tokenCacheTtl;
  private Optional<String> manualTokenOverride = Optional.empty();

  private static final Logger log = LoggerFactory.getLogger(AzureCredentialConfig.class);

  public AzureCredentialConfig() {
    log.info("Initializing manualTokenOverride=(empty)");
  }

  public Duration getTokenAcquisitionTimeout() {
    return tokenAcquisitionTimeout;
  }

  public void setTokenAcquisitionTimeoutSeconds(long tokenAcquisitionTimeoutSeconds) {
    var transformed = Duration.ofSeconds(tokenAcquisitionTimeoutSeconds);
    log.info("Setting tokenAcquisitionTimeout={}", transformed);
    this.tokenAcquisitionTimeout = transformed;
  }

  public Duration getTokenCacheTtl() {
    return tokenCacheTtl;
  }

  public void setTokenCacheTtlSeconds(long tokenCacheTtlSeconds) {
    var transformed = Duration.ofSeconds(tokenCacheTtlSeconds);
    log.info("Setting tokenCacheTtl={}", transformed);
    this.tokenCacheTtl = transformed;
  }

  public Optional<String> getManualTokenOverride() {
    return manualTokenOverride;
  }

  public void setManualTokenOverride(String manualTokenOverride) {
    var transformed = Optional.ofNullable(manualTokenOverride);
    if (log.isInfoEnabled()) {
      log.info("Setting manualTokenOverride={}", transformed.map(s -> "(set)").orElse("(empty)"));
    }
    this.manualTokenOverride = transformed;
  }
}
