package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class CodeSystemVersion extends CodeSystemVersionReference {
  private String codeSystem;
  private String preferredLanguage;
  private List<String> supportedLanguages;
  private LocalizedName description;
  private LocalDate releaseDate;
  private LocalDate expirationDate;
  private OffsetDateTime created;

  private Integer conceptsTotal;

  private List<CodeSystemEntityVersion> entities;
}
