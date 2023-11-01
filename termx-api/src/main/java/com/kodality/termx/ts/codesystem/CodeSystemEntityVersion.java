package com.kodality.termx.ts.codesystem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

  private List<CodeSystemVersionReference> versions;

  private Long codeSystemEntityId;
  private String sysModifiedBy;
  private OffsetDateTime sysModifiedAt;

  @JsonIgnore
  public Optional<Object> getPropertyValue(String propertyName){
    return this.getPropertyValues().stream()
        .filter(p -> p.getEntityProperty().equals(propertyName))
        .findFirst().map(EntityPropertyValue::getValue);
  }
}
