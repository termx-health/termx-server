package com.kodality.termserver.integration.fileimporter.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;


@Getter
public class FileProcessingResponse {
  private final List<FileProcessingResponseCodeSystem> codeSystems;

  public FileProcessingResponse(List<Map<String, FileProcessingResponseProperty>> entities) {
    this.codeSystems = entities.stream().map(FileProcessingResponseCodeSystem::new).collect(Collectors.toList());
  }

  public record FileProcessingResponseCodeSystem(Map<String, FileProcessingResponseProperty> entities) {
    public static final String IDENTIFIER = "identifier";
    public static final String ALIAS = "alias";
    public static final String DISPLAY = "display";
    public static final String DESIGNATION = "designation";
    public static final String PARENT = "parent";
    public static final String LEVEL = "level";
    public static final String VALID_FROM = "validFrom";
    public static final String VALID_TO = "validTo";
    public static final String STATUS = "status";
    public static final String DESCRIPTION = "description";
    public static final String MODIFIED_AT = "modifiedAt";

    public FileProcessingResponseProperty getIdentifier() {
      return entities.get(IDENTIFIER);
    }

    public FileProcessingResponseProperty getAlias() {
      return entities.get(ALIAS);
    }

    public FileProcessingResponseProperty getDisplay() {
      return entities.get(DISPLAY);
    }

    public FileProcessingResponseProperty getDesignation() {
      return entities.get(DESIGNATION);
    }

    public FileProcessingResponseProperty getParent() {
      return entities.get(PARENT);
    }

    public FileProcessingResponseProperty getLevel() {
      return entities.get(LEVEL);
    }

    public FileProcessingResponseProperty getValidFrom() {
      return entities.get(VALID_FROM);
    }

    public FileProcessingResponseProperty getValidTo() {
      return entities.get(VALID_TO);
    }

    public FileProcessingResponseProperty getStatus() {
      return entities.get(STATUS);
    }

    public FileProcessingResponseProperty getDescription() {
      return entities.get(DESCRIPTION);
    }

    public FileProcessingResponseProperty getModifiedAt() {
      return entities.get(MODIFIED_AT);
    }
  }

  @Getter
  @Setter
  public static class FileProcessingResponseProperty {
    private String columnName;
    private String propertyType;
    private String typeFormat;
    private String lang;
    private Object value;
  }
}
