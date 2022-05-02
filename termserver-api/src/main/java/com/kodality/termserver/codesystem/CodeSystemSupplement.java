package com.kodality.termserver.codesystem;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class CodeSystemSupplement {
  private Long id;
  private String codeSystem;
  private String type;
  private String description;
  private OffsetDateTime created;

  private Designation designationSupplement;
  private EntityProperty propertySupplement;
  private EntityPropertyValue propertyValueSupplement;
}
