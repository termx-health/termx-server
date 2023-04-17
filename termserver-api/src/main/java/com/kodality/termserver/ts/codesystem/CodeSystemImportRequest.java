package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.LocalizedName;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
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

  private List<Pair<String, String>> properties; //code and type
  private List<Pair<String, String>> associations; //code and kind

  private boolean activate = true;

  public CodeSystemImportRequest(CodeSystemImportConfiguration configuration) {
    this.codeSystem = new CodeSystemImportRequestCodeSystem().setId(configuration.getCodeSystem())
        .setUri(configuration.getUri())
        .setNames(configuration.getCodeSystemName())
        .setDescription(configuration.getCodeSystemDescription())
        .setBaseCodeSystem(configuration.getBaseCodeSystem());

    this.version = new CodeSystemImportRequestVersion().setVersion(configuration.getVersion())
        .setReleaseDate(configuration.getValidFrom())
        .setExpirationDate(configuration.getValidTo())
        .setSource(configuration.getSource())
        .setDescription(configuration.getCodeSystemVersionDescription());
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemImportRequestCodeSystem {
    private String id;
    private String uri;
    private LocalizedName names;
    private String content;
    private String caseSensitive;
    private String narrative;
    private String description;
    private List<String> supportedLanguages;

    private String baseCodeSystem;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemImportRequestVersion {
    private String version;
    private String source;
    private List<String> supportedLanguages;
    private String description;
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
}


