package com.kodality.termx.fileimporter.analyze;

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
  private List<FileAnalysisColumn> columns;

  @Getter
  @Setter
  public static class FileAnalysisColumn {
    private String columnName;
    private String columnType;
    private String columnTypeFormat;
    private boolean hasValues;
  }
}

