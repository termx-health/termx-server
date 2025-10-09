package com.kodality.termx.ts.codesystem;

import java.time.LocalDate;

import com.kodality.termx.ts.VersionReference;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemVersionReference extends VersionReference<CodeSystemVersionReference> {
  private String uri;
  private String status;
  private String preferredLanguage;
  private LocalDate releaseDate;

  private CodeSystemVersionReference baseCodeSystemVersion;
}
