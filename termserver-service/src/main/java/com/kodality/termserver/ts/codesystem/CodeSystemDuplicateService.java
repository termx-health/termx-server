package com.kodality.termserver.ts.codesystem;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
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
import liquibase.repackaged.org.apache.commons.collections4.MapUtils;
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

  private final UserPermissionService userPermissionService;

  @Transactional
  public void duplicateCodeSystem(CodeSystem targetCodeSystem, String sourceCodeSystem) {
    userPermissionService.checkPermitted(targetCodeSystem.getId(), "CodeSystem", "edit");

    CodeSystem sourceCs = codeSystemService.load(sourceCodeSystem, true).orElse(null);
    if (sourceCs == null) {
      throw ApiError.TE201.toApiException(Map.of("codeSystem", sourceCodeSystem));
    }

    if (codeSystemService.load(targetCodeSystem.getId()).isEmpty()) {
      targetCodeSystem.setNames(sourceCs.getNames());
      targetCodeSystem.setContent(sourceCs.getContent());
      targetCodeSystem.setContacts(sourceCs.getContacts());
      targetCodeSystem.setCaseSensitive(sourceCs.getCaseSensitive());
      targetCodeSystem.setNarrative(sourceCs.getNarrative());
      targetCodeSystem.setDescription(sourceCs.getDescription());
      targetCodeSystem.setBaseCodeSystem(sourceCs.getId());
      codeSystemService.save(targetCodeSystem);
    }

    List<CodeSystemVersion> versions = sourceCs.getVersions();
    versions.forEach(v -> v.setId(null).setCreated(null).setStatus(PublicationStatus.draft).setCodeSystem(targetCodeSystem.getId()));
    codeSystemVersionService.save(versions, targetCodeSystem.getId());

    Map<Long, Long> propertyMap = new HashMap<>();
    List<EntityProperty> properties = sourceCs.getProperties();
    properties.forEach(p -> {
      Long sourceId = p.getId();
      p.setId(null);
      entityPropertyService.save(p, targetCodeSystem.getId());
      propertyMap.put(sourceId, p.getId());
    });
    entityPropertyService.save(properties, targetCodeSystem.getId());

    duplicateConcepts(versions, sourceCodeSystem, null, targetCodeSystem.getId(), propertyMap);
  }

  @Transactional
  public void duplicateCodeSystemVersion(String targetVersionVersion, String targetCodeSystem, String sourceVersionVersion, String sourceCodeSystem) {
    userPermissionService.checkPermitted(targetCodeSystem, "CodeSystem", "edit");

    CodeSystemVersion version = codeSystemVersionService.load(sourceCodeSystem, sourceVersionVersion).orElse(null);
    if (version == null) {
      throw ApiError.TE202.toApiException(Map.of("version", sourceVersionVersion, "codeSystem", sourceCodeSystem));
    }

    version.setId(null);
    version.setVersion(targetVersionVersion);
    version.setCodeSystem(targetCodeSystem);
    version.setStatus(PublicationStatus.draft);
    version.setCreated(null);
    codeSystemVersionService.save(version);


    Map<Long, Long> propertyMap = new HashMap<>();
    if (!sourceCodeSystem.equals(targetCodeSystem)) {
      EntityPropertyQueryParams propertyParams = new EntityPropertyQueryParams().setCodeSystem(sourceCodeSystem);
      propertyParams.all();
      List<EntityProperty> properties = entityPropertyService.query(propertyParams).getData();
      properties.forEach(p -> {
        Long sourceId = p.getId();
        p.setId(null);
        entityPropertyService.save(p, targetCodeSystem);
        propertyMap.put(sourceId, p.getId());
      });
    }

    duplicateConcepts(List.of(version), sourceCodeSystem, sourceVersionVersion, targetCodeSystem, propertyMap);
  }

  private void duplicateConcepts(List<CodeSystemVersion> versions, String sourceCodeSystem, String sourceVersionVersion, String targetCodeSystem, Map<Long, Long> propertyMap) {
    if (sourceCodeSystem.equals(targetCodeSystem)) {
      duplicateEntityVersionMembership(versions, sourceCodeSystem, sourceVersionVersion, null);
    } else {
      ConceptQueryParams conceptParams = new ConceptQueryParams().setCodeSystem(sourceCodeSystem);
      conceptParams.all();
      List<Concept> concepts = conceptService.query(conceptParams).getData();

      Map<Long, Long> entityVersionsMap = duplicateConcepts(concepts, targetCodeSystem, propertyMap);
      duplicateEntityVersionMembership(versions, sourceCodeSystem, sourceVersionVersion, entityVersionsMap);
      duplicateEntityVersionAssociations(concepts, entityVersionsMap);
    }

  }

  private void duplicateEntityVersionMembership(List<CodeSystemVersion> versions, String sourceCodeSystem, String sourceVersionVersion,
                                                Map<Long, Long> entityVersionsMap) {
    versions.forEach(v -> {
      CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams();
      codeSystemEntityVersionParams.setCodeSystem(sourceCodeSystem);
      codeSystemEntityVersionParams.setCodeSystemVersion(sourceVersionVersion == null ? v.getVersion() : sourceVersionVersion);
      codeSystemEntityVersionParams.all();
      List<CodeSystemEntityVersion> entityVersions = codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData();
      codeSystemVersionService.saveEntityVersions(
          v.getId(),
          MapUtils.isNotEmpty(entityVersionsMap) ?
              entityVersions.stream().peek(ev -> ev.setId(entityVersionsMap.get(ev.getId()))).collect(Collectors.toList()) : entityVersions
      );
    });
  }

  private Map<Long, Long> duplicateConcepts(List<Concept> concepts, String targetCodeSystem, Map<Long, Long> propertyMap) {
    Map<Long, Long> entityVersionsMap = new HashMap<>();
    concepts.forEach(c -> {
      c.setId(null);
      conceptService.save(c, targetCodeSystem);
      c.getVersions().forEach(v -> {
        Long sourceId = v.getId();
        v.setId(null);
        v.setCodeSystem(targetCodeSystem);
        v.getDesignations().forEach(d -> d.setId(null).setDesignationTypeId(propertyMap.getOrDefault(d.getDesignationTypeId(), d.getDesignationTypeId())));
        v.getPropertyValues().forEach(pv -> pv.setId(null).setEntityPropertyId(propertyMap.getOrDefault(pv.getEntityPropertyId(), pv.getEntityPropertyId())));
        v.setAssociations(new ArrayList<>());
        v.setStatus(PublicationStatus.draft);
        codeSystemEntityVersionService.save(v, c.getId());
        entityVersionsMap.put(sourceId, v.getId());
      });
    });
    return entityVersionsMap;
  }


  private void duplicateEntityVersionAssociations(List<Concept> concepts, Map<Long, Long> entityVersionsMap) {
    concepts.forEach(concept -> concept.getVersions().forEach(version -> {
      Optional<Long> sourceVersionId = entityVersionsMap.entrySet().stream().filter(es -> es.getValue().equals(version.getId())).findFirst().map(Entry::getKey);
      if (sourceVersionId.isPresent()) {
        List<CodeSystemAssociation> associations = codeSystemAssociationService.loadAll(sourceVersionId.get());
        associations.forEach(a -> {
          a.setId(null);
          a.setTargetId(entityVersionsMap.get(a.getTargetId()));
        });
        codeSystemAssociationService.save(associations, version.getId(), version.getCodeSystem());
      }
    }));
  }
}
