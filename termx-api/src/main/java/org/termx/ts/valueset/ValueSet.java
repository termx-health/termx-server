package org.termx.ts.valueset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import org.termx.commons.UniqueResource;
import org.termx.ts.ConfigurationAttribute;
import org.termx.ts.ContactDetail;
import org.termx.ts.Copyright;
import org.termx.ts.OtherTitle;
import org.termx.ts.Permissions;
import org.termx.ts.PublicationStatus;
import org.termx.ts.Topic;
import org.termx.ts.UseContext;
import io.micronaut.core.annotation.Introspected;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class ValueSet extends UniqueResource<ValueSet> {
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

  private ValueSetSettings settings;

  private List<ValueSetVersion> versions;
  private List<ValueSetSnapshot> snapshots;
  @JsonProperty("lastVersion")
  private ValueSetVersion latestVersion;


  @JsonIgnore
  public Optional<ValueSetVersion> getFirstVersion() {
    return this.getVersions().stream().min(Comparator.comparing(ValueSetVersion::getReleaseDate));
  }

  @JsonIgnore
  public Optional<ValueSetVersion> getLastVersion() {
    return this.getVersions().stream()
        .filter(v -> !PublicationStatus.retired.equals(v.getStatus()))
        .max(Comparator.comparing(ValueSetVersion::getReleaseDate));
  }

  @Getter
  @Setter
  private static class ValueSetSettings {
    private boolean reviewRequired;
    private boolean approvalRequired;
  }
}
