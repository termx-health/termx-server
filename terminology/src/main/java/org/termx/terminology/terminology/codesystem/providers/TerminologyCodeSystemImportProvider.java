package org.termx.terminology.terminology.codesystem.providers;

import org.termx.terminology.terminology.codesystem.CodeSystemImportService;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.core.ts.CodeSystemImportProvider;
import org.termx.ts.PublicationStatus;
import org.termx.ts.association.AssociationType;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemContent;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemImportAction;
import org.termx.ts.codesystem.CodeSystemImportRequest;
import org.termx.ts.codesystem.CodeSystemImportSummary;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.EntityProperty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyCodeSystemImportProvider extends CodeSystemImportProvider {
  private final CodeSystemImportService codeSystemImportService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ConceptService conceptService;

  @Override
  public CodeSystemImportSummary importCodeSystem(CodeSystemImportRequest request) {
    CodeSystem codeSystem = toCodeSystem(request);
    // Compute the added / updated split BEFORE the import touches the DB so the email
    // notification (built from ImportLog.successes in the calling service) can say
    // "N added, M updated" instead of just "imported". One bulk lookup against
    // terminology.concept by codeSystem — negligible compared to the actual save.
    List<String> incomingCodes = codeSystem.getConcepts().stream().map(Concept::getCode).toList();
    Set<String> preExistingCodes = loadExistingConceptCodes(codeSystem.getId());
    int added = 0;
    for (String code : incomingCodes) {
      if (!preExistingCodes.contains(code)) {
        added++;
      }
    }
    int updated = incomingCodes.size() - added;

    List<AssociationType> associationTypes = toAssociationTypes(request);
    CodeSystemImportAction action = new CodeSystemImportAction()
        .setActivate(request.isActivate())
        .setCleanRun(request.isCleanRun())
        .setCleanConceptRun(request.isCleanConceptRun())
        .setGenerateValueSet(request.isGenerateValueSet());
    codeSystemImportService.importCodeSystem(codeSystem, associationTypes, action);

    return new CodeSystemImportSummary()
        .setCodeSystem(codeSystem.getId())
        .setVersion(codeSystem.getVersions().getFirst().getVersion())
        .setTotalConcepts(incomingCodes.size())
        .setAddedConcepts(added)
        .setUpdatedConcepts(updated);
  }

  /** Snapshot of existing concept codes in a code system, used to compute the
   *  added / updated split for {@link CodeSystemImportSummary}. Returns an empty set
   *  if the code system doesn't exist yet (first-ever import of that code system). */
  private Set<String> loadExistingConceptCodes(String codeSystemId) {
    if (codeSystemId == null) {
      return Set.of();
    }
    ConceptQueryParams params = new ConceptQueryParams();
    params.setCodeSystem(codeSystemId);
    params.setLimit(-1); // -1 means "all pages" in commons-db query convention
    try {
      return conceptService.query(params).getData().stream()
          .map(Concept::getCode)
          .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    } catch (Exception e) {
      // If the lookup fails (e.g. the table doesn't exist yet on a fresh DB), assume the
      // import is greenfield. Worse case the summary says "all added" instead of split —
      // never blocks the import itself.
      return new HashSet<>();
    }
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
      property.setRule(p.getRule());
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
