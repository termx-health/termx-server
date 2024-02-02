package com.kodality.termx.terminology.terminology.codesystem;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
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
  public void duplicateCodeSystem(CodeSystem targetCodeSystem, String sourceCodeSystem) {
    CodeSystem sourceCs = codeSystemService.load(sourceCodeSystem, true).orElse(null);
    if (sourceCs == null) {
      throw ApiError.TE201.toApiException(Map.of("codeSystem", sourceCodeSystem));
    }

    if (codeSystemService.load(targetCodeSystem.getId()).isEmpty()) {
      targetCodeSystem.setPublisher(sourceCs.getPublisher());
      targetCodeSystem.setTitle(sourceCs.getTitle());
      targetCodeSystem.setName(sourceCs.getName());
      targetCodeSystem.setDescription(sourceCs.getDescription());
      targetCodeSystem.setPurpose(sourceCs.getPurpose());
      targetCodeSystem.setHierarchyMeaning(sourceCs.getHierarchyMeaning());
      targetCodeSystem.setNarrative(sourceCs.getNarrative());
      targetCodeSystem.setExperimental(sourceCs.getExperimental());
      targetCodeSystem.setIdentifiers(sourceCs.getIdentifiers());
      targetCodeSystem.setContacts(sourceCs.getContacts());
      targetCodeSystem.setContent(sourceCs.getContent());
      targetCodeSystem.setCaseSensitive(sourceCs.getCaseSensitive());
      targetCodeSystem.setBaseCodeSystem(sourceCs.getId());
      codeSystemService.save(targetCodeSystem);
    }

    List<CodeSystemVersion> versions = sourceCs.getVersions(); //TODO load versions
    versions.forEach(v -> {
      v.setId(null);
      v.setStatus(PublicationStatus.draft);
      v.setCreated(null).setCodeSystem(targetCodeSystem.getId());
    });
    codeSystemVersionService.save(versions, targetCodeSystem.getId());

    Map<Long, Long> propertyMap = new HashMap<>();
    List<EntityProperty> properties = sourceCs.getProperties();
    properties.forEach(p -> {
      Long sourceId = p.getId();
      p.setId(null);
      entityPropertyService.save(targetCodeSystem.getId(), p);
      propertyMap.put(sourceId, p.getId());
    });
    entityPropertyService.save(targetCodeSystem.getId(), properties);

    duplicateConcepts(versions, sourceCodeSystem, null, targetCodeSystem.getId(), propertyMap);
  }

  @Transactional
  public CodeSystemVersion duplicateCodeSystemVersion(String targetVersionVersion, String targetCodeSystem, String sourceVersionVersion, String sourceCodeSystem) {
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
      Map<Long, EntityProperty> propertiesToSave = properties.stream().collect(Collectors.toMap(EntityProperty::getId, p -> p));
      entityPropertyService.save(targetCodeSystem, propertiesToSave.values().stream().peek(p -> p.setId(null)).toList());
      propertyMap = propertiesToSave.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getId()));
    }

    duplicateConcepts(List.of(version), sourceCodeSystem, sourceVersionVersion, targetCodeSystem, propertyMap);
    return version;
  }

  private void duplicateConcepts(List<CodeSystemVersion> versions,
                                 String sourceCodeSystem, String sourceVersionVersion,
                                 String targetCodeSystem,
                                 Map<Long, Long> propertyMap) {
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
      List<CodeSystemEntityVersion> versionToSave = CollectionUtils.isNotEmpty(entityVersionsMap) ? entityVersions.stream().peek(ev -> ev.setId(entityVersionsMap.get(ev.getId()))).toList() : entityVersions;
      codeSystemVersionService.linkEntityVersions(v.getId(), versionToSave.stream().map(CodeSystemEntityVersion::getId).toList());
    });
  }

  private Map<Long, Long> duplicateConcepts(List<Concept> concepts, String targetCodeSystem, Map<Long, Long> propertyMap) {
    concepts.forEach(c -> c.setId(null));
    conceptService.batchSave(concepts, targetCodeSystem);
    concepts.forEach(c -> {
      c.setVersions(c.getVersions() == null ? new ArrayList<>() : c.getVersions());
      c.getVersions().forEach(v -> {
        v.setCodeSystemEntityId(c.getId());
        v.setCodeSystem(targetCodeSystem);
        v.setCreated(null);
        v.setDesignations(CollectionUtils.isEmpty(v.getDesignations()) ? new ArrayList<>() : v.getDesignations());
        v.setPropertyValues(CollectionUtils.isEmpty(v.getPropertyValues()) ? new ArrayList<>() : v.getPropertyValues());
        v.setAssociations(CollectionUtils.isEmpty(v.getAssociations()) ? new ArrayList<>() : v.getAssociations());
        v.getDesignations().forEach(d -> d.setId(null).setDesignationTypeId(propertyMap.getOrDefault(d.getDesignationTypeId(), d.getDesignationTypeId())));
        v.getPropertyValues().forEach(pv -> pv.setId(null).setEntityPropertyId(propertyMap.getOrDefault(pv.getEntityPropertyId(), pv.getEntityPropertyId())));
        v.setAssociations(new ArrayList<>());
        v.setStatus(PublicationStatus.draft);
      });
    });
    Map<Long, Pair<Long, CodeSystemEntityVersion>> versions = concepts.stream().flatMap(c -> c.getVersions().stream()).collect(Collectors.toMap(CodeSystemEntityVersion::getId, v -> Pair.of(v.getCodeSystemEntityId(), v)));
    Map<Long, List<CodeSystemEntityVersion>> batch = concepts.stream().flatMap(c -> c.getVersions().stream().peek(v -> v.setId(null))).collect(Collectors.groupingBy(CodeSystemEntityVersion::getCodeSystemEntityId));
    codeSystemEntityVersionService.batchSave(batch, targetCodeSystem);
    return versions.entrySet().stream().collect(Collectors.toMap(Entry::getKey, es -> es.getValue().getValue().getId()));
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
