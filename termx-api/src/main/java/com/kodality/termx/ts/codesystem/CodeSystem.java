package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.ContactDetail;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class CodeSystem {
  private String id;
  private String uri;
  private String publisher;
  private LocalizedName title;
  private LocalizedName name;
  private LocalizedName description;
  private LocalizedName purpose;
  private String hierarchyMeaning;
  private String narrative;
  private Boolean experimental;
  private List<Identifier> identifiers;
  private List<ContactDetail> contacts;
  private String content;
  private String caseSensitive;
  private String sequence;
  private Object copyright;
  private Object permissions;

  private String baseCodeSystem;

  private List<Concept> concepts;
  private List<EntityProperty> properties;
  private List<CodeSystemVersion> versions;
}
