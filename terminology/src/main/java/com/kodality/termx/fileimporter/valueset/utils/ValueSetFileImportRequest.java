package com.kodality.termx.fileimporter.valueset.utils;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.util.Map;
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

  private String space;
  private String spacePackage;

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
    private String publisher;
    private String name;
    private String oid;
    private LocalizedName title;
    private LocalizedName description;
    private Map<String, String> contact;
    private String endorser;
  }

  @Getter
  @Setter
  public static class FileProcessingValueSetVersion {
    private String number;
    private String status;
    private String language;
    private LocalDate releaseDate;
    private String algorithm;
    private FileProcessingValueSetVersionRule rule;
  }

  @Getter
  @Setter
  public static class FileProcessingValueSetVersionRule {
    private Long id;

    private String codeSystem;
    private Long codeSystemVersionId;

    private String codeSystemUri;
  }
}
