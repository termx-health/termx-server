package com.kodality.termx.fileimporter.valueset.utils;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ValueSetFileImportRequest {
  private String link;
  private String type; // json; fsh; csv; tsv;

  private FileProcessingValueSet valueSet;
  private FileProcessingValueSetVersion version;

  private FileProcessingMapping mapping;

  private boolean dryRun;

  private String importClass;

  @Getter
  @Setter
  public static class FileProcessingMapping {
    private String code;
    private String display;
  }

  @Getter
  @Setter
  public static class FileProcessingValueSet {
    private String id;
    private String uri;
    private String name;
    private String oid;
    private LocalizedName title;
    private LocalizedName description;
  }

  @Getter
  @Setter
  public static class FileProcessingValueSetVersion {
    private String number;
    private String status;
    private LocalDate releaseDate;
    private FileProcessingValueSetVersionRule rule;
  }

  @Getter
  @Setter
  public static class FileProcessingValueSetVersionRule {
    private Long id;

    private String codeSystem;
    private Long codeSystemVersionId;
  }
}
