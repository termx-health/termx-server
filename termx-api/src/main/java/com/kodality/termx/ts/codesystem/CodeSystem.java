package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.Permissions;
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
  private String name;
  private LocalizedName title;
  private LocalizedName description;
  private LocalizedName purpose;
  private String narrative;
  private Boolean experimental;
  private List<Identifier> identifiers;
  private List<ContactDetail> contacts;
  private Copyright copyright;
  private Permissions permissions;

  private String hierarchyMeaning;
  private String content;
  private String caseSensitive;
  private String sequence;
  private String baseCodeSystem;
  private String baseCodeSystemUri;
  private CodeSystemSettings settings;

  private List<Concept> concepts;
  private List<EntityProperty> properties;
  private List<CodeSystemVersion> versions;

  @Getter
  @Setter
  public static class CodeSystemSettings {
    private boolean reviewRequired;
    private boolean approvalRequired;
    private boolean disableHierarchyGrouping;
  }
}
