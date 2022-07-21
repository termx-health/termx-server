package com.kodality.termserver.integration.fileimporter.mapset.utils;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class MapSetFileImportRow {
  private String sourceCodeSystem;
  private String sourceVersion;
  private String sourceCode;

  private String targetCodeSystem;
  private String targetVersion;
  private String targetCode;

  private String equivalence;
  private String comment;

  private String dependsOnProperty;
  private String dependsOnSystem;
  private String dependsOnValue;
}
