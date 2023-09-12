package com.kodality.termx.ts.mapset;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.property.PropertyReference;
import com.kodality.termx.ts.codesystem.EntityPropertyRule;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetProperty extends PropertyReference {
  private EntityPropertyRule rule;
  private String status;
  private LocalizedName description;
  private Integer orderNumber;
  private boolean required;
  private OffsetDateTime created;

  private Long definedEntityPropertyId;
}
