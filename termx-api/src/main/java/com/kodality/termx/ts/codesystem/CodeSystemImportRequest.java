package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.LocalizedName;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.tuple.Pair;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CodeSystemImportRequest {
  private CodeSystemImportRequestCodeSystem codeSystem;
  private CodeSystemImportRequestVersion version;
  private List<CodeSystemImportRequestConcept> concepts;

  private List<CodeSystemImportRequestProperty> properties;
  private List<Pair<String, String>> associations; //code and kind

  private boolean activate = true;
  private boolean generateValueSet;
  private boolean cleanRun;

  public CodeSystemImportRequest(CodeSystemImportConfiguration configuration) {
    this.codeSystem = new CodeSystemImportRequestCodeSystem().setId(configuration.getCodeSystem())
        .setUri(configuration.getUri())
        .setPublisher(configuration.getPublisher())
        .setTitle(configuration.getCodeSystemName())
        .setDescription(configuration.getCodeSystemDescription())
        .setBaseCodeSystem(configuration.getBaseCodeSystem());

    this.version = new CodeSystemImportRequestVersion().setVersion(configuration.getVersion())
        .setReleaseDate(configuration.getValidFrom())
        .setExpirationDate(configuration.getValidTo())
        .setDescription(configuration.getCodeSystemVersionDescription());

    this.cleanRun = configuration.isCleanRun();
    this.generateValueSet = configuration.isGenerateValueSet();
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemImportRequestCodeSystem {
    private String id;
    private String uri;
    private String publisher;
    private LocalizedName title;
    private String content;
    private String caseSensitive;
    private LocalizedName description;
    private List<String> supportedLanguages;
    private String hierarchyMeaning;

    private String baseCodeSystem;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemImportRequestVersion {
    private String version;
    private List<String> supportedLanguages;
    private LocalizedName description;
    private LocalDate releaseDate;
    private LocalDate expirationDate;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemImportRequestConcept {
    private String code;
    private List<Designation> designations;
    private List<EntityPropertyValue> propertyValues;
    private List<CodeSystemAssociation> associations;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemImportRequestProperty {
    private String name;
    private String type;
    private String kind;
  }
}


