package com.kodality.commons.drools;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DroolsExecuteRequest {
  private String ruleContext;
  private String rule;
  private String input;
}
