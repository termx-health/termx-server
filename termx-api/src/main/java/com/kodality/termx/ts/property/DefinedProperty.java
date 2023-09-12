package com.kodality.termx.ts.property;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.codesystem.EntityPropertyRule;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class DefinedProperty extends PropertyReference {
  private EntityPropertyRule rule;
  private LocalizedName description;
  private boolean used; // calculated field
}
