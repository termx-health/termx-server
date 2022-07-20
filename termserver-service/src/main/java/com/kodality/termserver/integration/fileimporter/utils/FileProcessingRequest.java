package com.kodality.termserver.integration.fileimporter.utils;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class FileProcessingRequest {
  private String link;
  private String type; // csv; tsv
  private String template;
  private List<FileProcessingProperty> properties;

  private boolean generateValueSet;

  @Getter
  @Setter
  public static class FileProcessingCodeSystem {
    private String id;
    private String uri;
    private LocalizedName names;
    private String description;
  }

  @Getter
  @Setter
  public static class FileProcessingCodeSystemVersion {
    private String version;
    private String status;
    private String releaseDate;
  }

  @Getter
  @Setter
  public static class FileProcessingProperty {
    private String columnName;
    private String mappedProperty;
    private String propertyType;
    private String typeFormat;
    private String lang;
  }
}
