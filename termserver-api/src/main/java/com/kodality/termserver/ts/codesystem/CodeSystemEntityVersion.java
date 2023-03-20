package com.kodality.termserver.ts.codesystem;

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
public class CodeSystemEntityVersion {
  private Long id;
  private String code;
  private String codeSystem;
  private String description;
  private String status;
  private OffsetDateTime created;

  private List<EntityPropertyValue> propertyValues;
  private List<Designation> designations;
  private List<CodeSystemAssociation> associations;

  private String codeSystemVersion;

  private Long codeSystemEntityId;
}
