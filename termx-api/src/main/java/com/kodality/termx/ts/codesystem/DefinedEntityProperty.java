package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class DefinedEntityProperty extends EntityPropertyReference {
  private EntityPropertyRule rule;
  private LocalizedName description;
  private boolean used; // calculated field
}
