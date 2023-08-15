package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class DefinedEntityProperty {
  private Long id;
  private String name;
  private String type;
  private String kind; // designation, property
  private String uri;
  private EntityPropertyRule rule;
  private LocalizedName description;

  private boolean used; // calculated field

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EntityPropertyRule {
    private List<String> codeSystems;
    private String valueSet;
    private List<EntityPropertyRuleFilter> filters;
  }

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
