package com.kodality.termserver.ts.codesystem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kodality.termserver.ts.PublicationStatus;
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

  @JsonIgnore
  public Optional<CodeSystemEntityVersion> getLastActiveVersion() {
    return this.getVersions().stream()
        .filter(v -> !PublicationStatus.retired.equals(v.getStatus()))
        .max(Comparator.comparing(CodeSystemEntityVersion::getCreated));
  }
}
