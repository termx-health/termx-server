package com.kodality.termserver.fileimporter.codesystem.utils;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class FileProcessingRequest {
  private String link;
  private String type; // csv; tsv
  private boolean generateValueSet;
  private List<FileProcessingProperty> properties;

  private FileProcessingCodeSystem codeSystem;
  private FileProcessingCodeSystemVersion version;


  @Getter
  @Setter
  public static class FileProcessingProperty {
    private String columnName;
    private String propertyName;
    private String propertyType;
    private String propertyTypeFormat;
    private boolean preferred;
    private String lang;

    public String getName() {
      return propertyName != null ? propertyName : columnName;
    }
  }

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
    private LocalDate releaseDate;

  }
}
