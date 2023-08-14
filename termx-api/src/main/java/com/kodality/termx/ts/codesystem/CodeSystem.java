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
  private String name;
  private LocalizedName title;
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
  private CodeSystemCopyright copyright;
  private Object permissions;
  private CodeSystemSettings settings;

  private String baseCodeSystem;

  private List<Concept> concepts;
  private List<EntityProperty> properties;
  private List<CodeSystemVersion> versions;

  @Getter
  @Setter
  public static class CodeSystemSettings {
    private boolean reviewRequired;
    private boolean approvalRequired;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemCopyright {
    private String holder;
    private String jurisdiction;
    private String statement;
  }
}
