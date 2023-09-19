package com.kodality.termx.fileimporter.codesystem.utils;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class CodeSystemFileImportRequest {
  private String link;
  private String type; // csv; tsv; json; fsh

  private FileProcessingCodeSystem codeSystem;
  private FileProcessingCodeSystemVersion version;

  private List<FileProcessingProperty> properties;
  private boolean generateValueSet;
  private boolean dryRun;
  private boolean cleanVersion;
  private boolean replaceConcept;

  private String importClass;

  @Getter
  @Setter
  public static class FileProcessingProperty {
    private String columnName;
    private String propertyName;
    private String propertyType;
    private String propertyTypeFormat;
    private String propertyDelimiter;
    private boolean preferred;
    private String language;

    public String getName() {
      return propertyName != null ? propertyName : columnName;
    }
  }

  @Getter
  @Setter
  public static class FileProcessingCodeSystem {
    private String id;
    private String uri;
    private String name;
    private String oid;
    private LocalizedName title;
    private LocalizedName description;
  }

  @Getter
  @Setter
  public static class FileProcessingCodeSystemVersion {
//    private Long id;
    private String number;
    private String status;
    private LocalDate releaseDate;
  }
}
