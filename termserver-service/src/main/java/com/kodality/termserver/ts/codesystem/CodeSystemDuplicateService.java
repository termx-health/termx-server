package com.kodality.termserver.ts.codesystem;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemDuplicateService {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemAssociationService codeSystemAssociationService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Transactional
  public void duplicate(CodeSystem targetCodeSystem, String sourceCsId) {
    CodeSystem sourceCodeSystem = codeSystemService.get(sourceCsId).orElse(null);
    if (sourceCodeSystem == null) {
      throw ApiError.TE111.toApiException(Map.of("codeSystem", sourceCsId));
    }

    if (codeSystemService.get(targetCodeSystem.getId()).isEmpty()) {
      targetCodeSystem.setNames(sourceCodeSystem.getNames());
      targetCodeSystem.setDescription(sourceCodeSystem.getDescription());
      codeSystemService.save(targetCodeSystem);
    }

    List<CodeSystemVersion> versions = codeSystemVersionService.getVersions(sourceCsId);
    versions.forEach(v -> v.setId(null).setStatus(PublicationStatus.draft).setCodeSystem(targetCodeSystem.getId()));
    codeSystemVersionService.save(versions, targetCodeSystem.getId());

    List<EntityProperty> properties = entityPropertyService.getProperties(sourceCsId);
    properties.forEach(p -> p.setId(null));
    entityPropertyService.save(properties, targetCodeSystem.getId());

    ConceptQueryParams conceptParams = new ConceptQueryParams().setCodeSystem(sourceCsId);
    conceptParams.all();
    List<Concept> concepts = conceptService.query(conceptParams).getData();
    Map<Long, Long> entityVersionsMap = new HashMap<>();
    concepts.forEach(c -> {
      c.setId(null);
      conceptService.save(c, targetCodeSystem.getId());
      c.getVersions().forEach(v -> {
        Long sourceId = v.getId();
        v.setId(null);
        v.getDesignations().forEach(d -> d.setId(null));
        v.getPropertyValues().forEach(pv -> pv.setId(null));
        v.setAssociations(new ArrayList<>());
        v.setStatus(PublicationStatus.draft);
        codeSystemEntityVersionService.save(v, c.getId());
        entityVersionsMap.put(sourceId, v.getId());
      });
    });

    versions.forEach(v -> {
      CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams()
          .setCodeSystem(sourceCsId).setCodeSystemVersion(v.getVersion());
      codeSystemEntityVersionParams.all();
      List<CodeSystemEntityVersion> entityVersions = codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData();
      codeSystemVersionService.saveEntityVersions(v.getId(),
          entityVersions.stream().peek(ev -> ev.setId(entityVersionsMap.get(ev.getId()))).collect(Collectors.toList()));
    });

    concepts.forEach(concept -> concept.getVersions().forEach(version -> {
      Optional<Long> sourceVersionId = entityVersionsMap.entrySet().stream().filter(es -> es.getValue().equals(version.getId())).findFirst().map(Entry::getKey);
      if (sourceVersionId.isPresent()) {
        List<CodeSystemAssociation> associations = codeSystemAssociationService.loadAll(sourceVersionId.get());
        associations.forEach(a -> {
          a.setId(null);
          a.setTargetId(entityVersionsMap.get(a.getTargetId()));
        });
        codeSystemAssociationService.save(associations, version.getId());
      }
    }));
  }
}
