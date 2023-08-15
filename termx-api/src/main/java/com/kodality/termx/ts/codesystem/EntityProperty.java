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
public class EntityProperty extends DefinedEntityProperty {
  private String status;
  private Integer orderNumber;
  private boolean preferred;
  private boolean required;
  private OffsetDateTime created;

  private Long definedEntityPropertyId;
}
