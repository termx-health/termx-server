package com.kodality.termserver.codesystem;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemEntityVersion {
  private Long id;
  private String code;
  private String description;
  private String status;
  private OffsetDateTime created;

  private int orderNr;

  private List<EntityPropertyValue> propertyValues;
  private List<Designation> designations;
  private List<CodeSystemAssociation> associations;

}
