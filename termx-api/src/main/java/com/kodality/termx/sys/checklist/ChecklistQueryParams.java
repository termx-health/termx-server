package com.kodality.termx.sys.checklist;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ChecklistQueryParams extends QueryParams {
  private String ids;
  private String resourceType;
  private String resourceId;
  private String resourceVersion;

  private String ruleTarget;
  private String ruleVerification;

  private boolean assertionsDecorated;
}
