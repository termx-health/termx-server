package com.kodality.termx.fileimporter.codesystem.utils;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemFileImportResult {
  private List<FileProcessingResponseProperty> properties;
  private List<Map<String, List<FileProcessingEntityPropertyValue>>> entities;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class FileProcessingResponseProperty {
    private String propertyType;
    private String propertyName;
    private String propertyKind;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class FileProcessingEntityPropertyValue {
    private String columnName;
    private String propertyName;
    private String propertyType;
    private String propertyTypeFormat;
    private String lang;
    private Object value;
  }
}
