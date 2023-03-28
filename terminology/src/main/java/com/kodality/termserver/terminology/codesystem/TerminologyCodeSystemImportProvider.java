package com.kodality.termserver.terminology.codesystem;

import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.CodeSystemImportProvider;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemContent;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyCodeSystemImportProvider extends CodeSystemImportProvider {
  private final CodeSystemImportService codeSystemImportService;

  @Override
  public void importCodeSystem(CodeSystemImportRequest request) {
    CodeSystem codeSystem = toCodeSystem(request);
    List<AssociationType> associationTypes = toAssociationTypes(request);
    codeSystemImportService.importCodeSystem(codeSystem, associationTypes, true);
  }

  private CodeSystem toCodeSystem(CodeSystemImportRequest request) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(request.getCodeSystem().getId());
    codeSystem.setUri(request.getCodeSystem().getUri());
    codeSystem.setNames(request.getCodeSystem().getNames());
    codeSystem.setDescription(request.getCodeSystem().getDescription());
    codeSystem.setContent(Optional.ofNullable(request.getCodeSystem().getContent()).orElse(CodeSystemContent.complete));
    codeSystem.setBaseCodeSystem(request.getCodeSystem().getBaseCodeSystem());

    codeSystem.setVersions(mapVersions(request));
    codeSystem.setProperties(mapProperties(request));
    codeSystem.setConcepts(mapConcepts(request));
    return codeSystem;
  }

  private List<Concept> mapConcepts(CodeSystemImportRequest request) {
    return Optional.ofNullable(request.getConcepts()).orElse(List.of()).stream().map(c -> {
      CodeSystemEntityVersion version = new CodeSystemEntityVersion();
      version.setCode(c.getCode());
      version.setCodeSystem(request.getCodeSystem().getId());
      version.setStatus(PublicationStatus.draft);
      version.setPropertyValues(Optional.ofNullable(c.getPropertyValues()).orElse(List.of()));
      version.setDesignations(Optional.ofNullable(c.getDesignations()).orElse(List.of()));
      version.setAssociations(Optional.ofNullable(c.getAssociations()).orElse(List.of()));
      Concept concept = new Concept();
      concept.setCode(c.getCode());
      concept.setVersions(List.of(version));
      return concept;
    }).toList();
  }

  private List<CodeSystemVersion> mapVersions(CodeSystemImportRequest request) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(request.getCodeSystem().getId());
    version.setVersion(request.getVersion().getVersion());
    version.setSource(request.getVersion().getSource());
    version.setSupportedLanguages(request.getVersion().getSupportedLanguages());
    version.setDescription(request.getVersion().getDescription());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(request.getVersion().getReleaseDate());
    version.setExpirationDate(request.getVersion().getExpirationDate());
    return List.of(version);
  }

  private List<EntityProperty> mapProperties(CodeSystemImportRequest request) {
    return Optional.ofNullable(request.getProperties()).orElse(List.of()).stream().map(p -> {
      EntityProperty property = new EntityProperty();
      property.setName(p.getKey());
      property.setType(p.getValue());
      property.setStatus(PublicationStatus.active);
      return property;
    }).toList();
  }

  public static List<AssociationType> toAssociationTypes(CodeSystemImportRequest request) {
    return Optional.ofNullable(request.getAssociations()).orElse(List.of()).stream().map(a -> {
      AssociationType type = new AssociationType();
      type.setCode(a.getKey());
      type.setAssociationKind(a.getValue());
      type.setDirected(true);
      return type;
    }).toList();
  }
}
