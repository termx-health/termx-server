package com.kodality.termserver.codesystem;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemVersion {
  private Long id;
  private String codeSystem;
  private String version;
  private String source;
  private String preferredLanguage;
  private List<String> supportedLanguages;
  private String description;
  private String status;
  private LocalDate releaseDate;
  private LocalDate expirationDate;
  private OffsetDateTime created;

  private List<CodeSystemEntityVersion> entities;
}
