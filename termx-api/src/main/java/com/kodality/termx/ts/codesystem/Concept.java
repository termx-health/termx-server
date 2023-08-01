package com.kodality.termx.ts.codesystem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kodality.termx.ts.PublicationStatus;
import io.micronaut.core.annotation.Introspected;
import java.util.Comparator;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class Concept extends CodeSystemEntity {
  private String code;
  private String description;

  private Boolean leaf;
  private Long childCount;
  private boolean immutable;

  @JsonIgnore
  public Optional<CodeSystemEntityVersion> getLastVersion() {
    return this.getVersions().stream()
        .filter(v -> !PublicationStatus.retired.equals(v.getStatus()))
        .max(Comparator.comparing(CodeSystemEntityVersion::getCreated));
  }

  @JsonIgnore
  public Optional<CodeSystemEntityVersion> getLastDraftVersion() {
    return this.getVersions().stream()
        .filter(v -> PublicationStatus.draft.equals(v.getStatus()))
        .max(Comparator.comparing(CodeSystemEntityVersion::getCreated));
  }


}
