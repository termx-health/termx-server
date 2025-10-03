package com.kodality.termx.ts.codesystem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.ConfigurationAttribute;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.OtherTitle;
import com.kodality.termx.ts.Permissions;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.Topic;
import com.kodality.termx.ts.UseContext;
import io.micronaut.core.annotation.Introspected;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class CodeSystem {
  @Pattern(regexp = "[A-Za-z0-9\\-\\.]{1,64}")
  private String id;
  private String uri;
  private String publisher;
  private String name;
  private List<OtherTitle> otherTitle;
  private LocalizedName title;
  private LocalizedName description;
  private LocalizedName purpose;
  private Topic topic;
  private List<UseContext> useContext;
  private String narrative;
  private Boolean experimental;
  private String sourceReference;
  private String replaces;
  private String externalWebSource;
  private List<Identifier> identifiers;
  private List<ConfigurationAttribute> configurationAttributes;
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

  private String valueSet;

  private List<Concept> concepts;
  private List<EntityProperty> properties;
  private List<CodeSystemVersion> versions;

  @JsonIgnore
  public Optional<CodeSystemVersion> getFirstVersion() {
    return this.getVersions().stream().min(Comparator.comparing(CodeSystemVersion::getReleaseDate));
  }

  @JsonIgnore
  public Optional<CodeSystemVersion> getLastVersion() {
    return this.getVersions().stream()
        .filter(v -> !PublicationStatus.retired.equals(v.getStatus()))
        .max(Comparator.comparing(CodeSystemVersion::getReleaseDate));
  }

  @Getter
  @Setter
  public static class CodeSystemSettings {
    private boolean reviewRequired;
    private boolean approvalRequired;
    private boolean disableHierarchyGrouping;
  }
}
