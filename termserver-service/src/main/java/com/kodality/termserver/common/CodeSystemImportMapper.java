package com.kodality.termserver.common;

import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemContent;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import java.util.ArrayList;
import java.util.List;

public class CodeSystemImportMapper {

  public static CodeSystem mapCodeSystem(ImportConfiguration configuration, String lang) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(configuration.getCodeSystem());
    codeSystem.setUri(configuration.getUri());
    codeSystem.setNames(configuration.getCodeSystemName());
    codeSystem.setDescription(configuration.getCodeSystemDescription());
    codeSystem.setVersions(mapVersions(configuration, lang));
    codeSystem.setContent(CodeSystemContent.complete);
    codeSystem.setBaseCodeSystem(configuration.getBaseCodeSystem());
    return codeSystem;
  }

  private static List<CodeSystemVersion> mapVersions(ImportConfiguration configuration, String lang) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(configuration.getCodeSystem());
    version.setVersion(configuration.getVersion());
    version.setSource(configuration.getSource());
    version.setPreferredLanguage(lang);
    version.setSupportedLanguages(List.of(lang));
    version.setDescription(configuration.getCodeSystemVersionDescription());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(configuration.getValidFrom());
    version.setExpirationDate(configuration.getValidTo());
    return List.of(version);
  }

  public static List<CodeSystemAssociation> mapAssociations(String targetCode, String associationType, ImportConfiguration configuration) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (targetCode == null) {
      return associations;
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setCodeSystem(configuration.getCodeSystem());
    association.setAssociationType(associationType);
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(targetCode);
    associations.add(association);
    return associations;
  }
}
