package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.property.PropertyReference;
import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class EntityProperty extends PropertyReference {
  private EntityPropertyRule rule;
  private LocalizedName description;
  private String status;
  private Integer orderNumber;
  private boolean preferred;
  private boolean required;
  private boolean showInList;
  private OffsetDateTime created;
  private String codeSystem;

  private Long definedEntityPropertyId;
}
