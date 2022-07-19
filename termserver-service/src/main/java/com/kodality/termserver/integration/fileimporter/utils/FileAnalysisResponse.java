package com.kodality.termserver.integration.fileimporter.utils;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class FileAnalysisResponse {
  private List<FileAnalyzeProperty> properties;

  @Getter
  @Setter
  public static class FileAnalyzeProperty {
    private String columnName;
    private String mappedProperty;
    private String propertyType;
    private String typeFormat;
    private boolean hasValues;
  }
}

