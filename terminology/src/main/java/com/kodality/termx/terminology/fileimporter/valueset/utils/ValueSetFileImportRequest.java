package com.kodality.termx.terminology.fileimporter.valueset.utils;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.util.List;
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
  private boolean cleanVersion;

  private String importClass;

  private String space;
  private String spacePackage;

  @Getter
  @Setter
  public static class FileProcessingMapping {
    private String code;
    private String display;

    // only for validation
    private String retirementDate;
    private String status;
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
    private String admin;
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
    private Boolean inactive;
    private FileProcessingValueSetVersionRule rule;
    private String oid;
  }

  @Getter
  @Setter
  public static class FileProcessingValueSetVersionRule {
    private Long id;

    private String codeSystem;
    private Long codeSystemVersionId;

    private List<String> properties;

    private String codeSystemUri;
  }
}
