package com.kodality.commons.drools;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DroolsValidateRequest {
  @NotNull
  private String rule;
}
