package com.kodality.termserver.job;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Introspected
@Getter
@Setter
@Accessors(chain = true)
public class JobLog {
  private Long id;
  private JobDefinition definition;
  private JobExecution execution;
  private Map<String, Object> warnings;
  private Map<String, Object> errors;

  @Introspected
  @Getter
  @Setter
  public static class JobDefinition {
    @NotNull
    private String type;
    @NotNull
    private String source;
  }

  @Getter
  @Setter
  public static class JobExecution {
    private OffsetDateTime started;
    private OffsetDateTime finished;
    private String status;
  }
}
