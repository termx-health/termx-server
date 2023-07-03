package com.kodality.termx.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class EntityProperty {
  private Long id;
  private String name;
  private String kind; // designation, property
  private String type;
  private String description;
  private String status;
  private Integer orderNumber;
  private boolean preferred;
  private boolean required;
  private OffsetDateTime created;
  private EntityPropertyRule rule;

  private Long supplementId;

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
