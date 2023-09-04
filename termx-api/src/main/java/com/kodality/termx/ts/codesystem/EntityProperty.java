package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class EntityProperty extends EntityPropertyReference {
  private EntityPropertyRule rule;
  private LocalizedName description;
  private String status;
  private Integer orderNumber;
  private boolean preferred;
  private boolean required;
  private OffsetDateTime created;

  private Long definedEntityPropertyId;
}
