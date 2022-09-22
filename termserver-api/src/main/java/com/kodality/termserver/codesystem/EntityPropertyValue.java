package com.kodality.termserver.codesystem;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class EntityPropertyValue {
  private Long id;
  private Object value;
  private Long entityPropertyId;
  private Long codeSystemEntityVersionId;

  private Long supplementId;

  private String entityProperty;
}
