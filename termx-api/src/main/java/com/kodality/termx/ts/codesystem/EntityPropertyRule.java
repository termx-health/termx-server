package com.kodality.termx.ts.codesystem;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EntityPropertyRule {
  private List<String> codeSystems;
  private String valueSet;
  private List<EntityPropertyRuleFilter> filters;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EntityPropertyRuleFilter {
    private String type;
    private String association;
    private EntityProperty property;
    private String operator;
    private String value;
  }
}
