package com.kodality.termserver.ts.valueset.concept;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.DesignationQueryParams;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.valueset.ValueSetVersionRepository;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionConceptQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionConceptService {
  private final DesignationService designationService;
  private final ValueSetExpandService valueSetExpandService;
  private final ValueSetVersionConceptRepository repository;
  private final ValueSetVersionRepository valueSetVersionRepository;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final UserPermissionService userPermissionService;

  public Optional<ValueSetVersionConcept> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public List<ValueSetVersionConcept> loadAll(Long valueSetVersionId) {
    return repository.loadAll(valueSetVersionId);
  }

  @Transactional
  public void save(List<ValueSetVersionConcept> concepts, Long valueSetVersionId) {
    repository.retain(concepts, valueSetVersionId);
    if (concepts != null) {
      concepts.forEach(concept -> save(concept, valueSetVersionId));
    }
  }

  @Transactional
  public void save(ValueSetVersionConcept concept, String valueSet, String valueSetVersion) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");

    ValueSetVersion version = valueSetVersionRepository.load(valueSet, valueSetVersion);
    if (version == null) {
      throw ApiError.TE301.toApiException(Map.of("version", valueSetVersion, "valueSet", valueSet));
    }
    repository.save(concept, version.getId());
  }

  @Transactional
  public void save(ValueSetVersionConcept concept, Long valueSetVersionId) {
    repository.save(concept, valueSetVersionId);
  }

  @Transactional
  public void delete(Long id, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");
    repository.delete(id);
  }

  public List<ValueSetVersionConcept> decorate(List<ValueSetVersionConcept> concepts) {
    List<String> designationIds = new ArrayList<>();
    designationIds.addAll(concepts.stream().filter(c -> c.getDisplay() != null && c.getDisplay().getId() != null)
        .map(c -> String.valueOf(c.getDisplay().getId())).toList());
    designationIds.addAll(concepts.stream().filter(c -> CollectionUtils.isNotEmpty(c.getAdditionalDesignations()))
        .flatMap(c -> c.getAdditionalDesignations().stream())
        .filter(ad -> ad.getId() != null).map(ad -> String.valueOf(ad.getId())).toList());
    DesignationQueryParams designationParams = new DesignationQueryParams();
    designationParams.setId(String.join(",", designationIds));
    designationParams.setLimit(designationIds.size());
    List<Designation> designations = designationService.query(designationParams).getData();

    List<String> conceptIds = concepts.stream().filter(c -> c.getConcept() != null && c.getConcept().getId() != null).map(c -> String.valueOf(c.getConcept().getId())).toList();
    CodeSystemEntityVersionQueryParams entityVersionParams = new CodeSystemEntityVersionQueryParams();
    entityVersionParams.setCodeSystemEntityIds(String.join(",", conceptIds));
    entityVersionParams.setStatus(PublicationStatus.active);
    entityVersionParams.all();
    List<CodeSystemEntityVersion> activeVersions = CollectionUtils.isEmpty(conceptIds) ? new ArrayList<>() : codeSystemEntityVersionService.query(entityVersionParams).getData();

    concepts.forEach(c -> {
      c.setDisplay(c.getDisplay() == null || c.getDisplay().getId() == null ? null : designations.stream().filter(d -> d.getId().equals(c.getDisplay().getId())).findFirst().orElse(c.getDisplay()));
      c.setActive(c.isActive() || activeVersions.stream().anyMatch(av -> av.getCode().equals(c.getConcept().getCode())));
      if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
        c.setAdditionalDesignations(c.getAdditionalDesignations().stream()
            .map(ad -> ad.getId() == null ? ad : designations.stream().filter(d -> d.getId().equals(ad.getId())).findFirst().orElse(ad))
            .collect(Collectors.toList()));
      }

      if (c.getDisplay() == null || c.getDisplay().getName() == null || CollectionUtils.isEmpty(c.getAdditionalDesignations())) {
        DesignationQueryParams params = new DesignationQueryParams().setConceptCode(c.getConcept().getCode()).setCodeSystem(c.getConcept().getCodeSystem());
        params.all();
        List<Designation> csDesignations = designationService.query(params).getData();
        designations.sort(Comparator.comparing(d -> !d.isPreferred()));
        c.setDisplay(c.getDisplay() == null || c.getDisplay().getName() == null ? designations.stream().findFirst().orElse(null) : c.getDisplay());
        c.setAdditionalDesignations(CollectionUtils.isEmpty(c.getAdditionalDesignations()) ? csDesignations : c.getAdditionalDesignations());
      }
    });
    return concepts;
  }

  public QueryResult<ValueSetVersionConcept> query(ValueSetVersionConceptQueryParams params) {
    if (params.getValueSetVersionId() == null) {
      return QueryResult.empty();
    }
    QueryResult<ValueSetVersionConcept> concepts = repository.query(params);
    if (params.isDecorated()) {
      concepts.setData(decorate(concepts.getData()));
    }
    return concepts;
  }

  public List<ValueSetVersionConcept> expand(String valueSet, String valueSetVersion, ValueSetVersionRuleSet ruleSet) {
    Long versionId = null;
    if (valueSet != null) {
      ValueSetVersion version = valueSetVersion == null ? valueSetVersionRepository.loadLastVersion(valueSet) : valueSetVersionRepository.load(valueSet, valueSetVersion);
      versionId = version == null ? null : version.getId();
    }

    List<ValueSetVersionConcept> internalExpand = internalExpand(versionId, ruleSet);
    internalExpand.addAll(valueSetExpandService.snomedExpand(versionId, ruleSet));
    return internalExpand;
  }

  private List<ValueSetVersionConcept> internalExpand(Long versionId, ValueSetVersionRuleSet ruleSet) {
    if (ruleSet != null) {
      return decorate(repository.expand(ruleSet));
    }
    if (versionId != null) {
      return decorate(repository.expand(versionId));
    }
    return new ArrayList<>();
  }

  @Transactional
  public void cancel(Long valueSetVersionId, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");
    save(List.of(), valueSetVersionId);
  }
}
