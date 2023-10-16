package com.kodality.termx.terminology.terminology.codesystem.providers;

import com.kodality.termx.terminology.terminology.codesystem.CodeSystemImportService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.core.ts.CodeSystemImportProvider;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemImportAction;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.EntityProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyCodeSystemImportProvider extends CodeSystemImportProvider {
  private final CodeSystemImportService codeSystemImportService;
  private final CodeSystemVersionService codeSystemVersionService;

  @Override
  public void importCodeSystem(CodeSystemImportRequest request) {
    CodeSystem codeSystem = toCodeSystem(request);
    List<AssociationType> associationTypes = toAssociationTypes(request);
    CodeSystemImportAction action = new CodeSystemImportAction()
        .setActivate(request.isActivate())
        .setCleanRun(request.isCleanRun())
        .setCleanConceptRun(request.isCleanConceptRun())
        .setGenerateValueSet(request.isGenerateValueSet());
    codeSystemImportService.importCodeSystem(codeSystem, associationTypes, action);
  }

  private CodeSystem toCodeSystem(CodeSystemImportRequest request) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(request.getCodeSystem().getId());
    codeSystem.setUri(request.getCodeSystem().getUri());
    codeSystem.setPublisher(request.getCodeSystem().getPublisher());
    codeSystem.setTitle(request.getCodeSystem().getTitle());
    codeSystem.setDescription(request.getCodeSystem().getDescription());
    codeSystem.setContent(Optional.ofNullable(request.getCodeSystem().getContent()).orElse(CodeSystemContent.complete));
    codeSystem.setBaseCodeSystem(request.getCodeSystem().getBaseCodeSystem());
    codeSystem.setHierarchyMeaning(request.getCodeSystem().getHierarchyMeaning());

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
      version.setPropertyValues(Optional.ofNullable(c.getPropertyValues()).orElse(new ArrayList<>()));
      version.setDesignations(Optional.ofNullable(c.getDesignations()).orElse(new ArrayList<>()));
      version.setAssociations(Optional.ofNullable(c.getAssociations()).orElse(new ArrayList<>()));
      Concept concept = new Concept();
      concept.setCode(c.getCode());
      concept.setVersions(List.of(version));
      return concept;
    }).toList();
  }

  private List<CodeSystemVersion> mapVersions(CodeSystemImportRequest request) {
    CodeSystemVersion version = new CodeSystemVersion();
    Optional<CodeSystemVersion> existingVersion = findVersion(request.getCodeSystem().getId(), request.getVersion().getVersion());
    if (existingVersion.isPresent()) {
      return List.of(existingVersion.get());
    }
    version.setCodeSystem(request.getCodeSystem().getId());
    version.setVersion(request.getVersion().getVersion());
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
      property.setName(p.getName());
      property.setType(p.getType());
      property.setKind(p.getKind());
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

  private Optional<CodeSystemVersion> findVersion(String csId, String version) {
    CodeSystemVersionQueryParams params = new CodeSystemVersionQueryParams();
    params.setVersion(version);
    params.setCodeSystem(csId);
    params.setLimit(1);
    return codeSystemVersionService.query(params).findFirst();
  }
}
