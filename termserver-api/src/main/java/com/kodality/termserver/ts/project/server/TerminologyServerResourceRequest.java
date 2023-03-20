package com.kodality.termserver.ts.project.server;

import io.micronaut.core.annotation.Introspected;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class TerminologyServerResourceRequest {
  @NotNull
  private String serverCode;
  @NotNull
  private String resourceType;
  @NotNull
  private String resourceId;
}
