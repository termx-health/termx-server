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
  private List<FileAnalysisProperty> properties;

  @Getter
  @Setter
  public static class FileAnalysisProperty {
    private String columnName;
    private String columnType;
    private String columnTypeFormat;
    private boolean hasValues;
  }
}

