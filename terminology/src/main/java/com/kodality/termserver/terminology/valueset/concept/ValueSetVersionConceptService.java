package com.kodality.termserver.terminology.valueset.concept;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.terminology.codesystem.designation.DesignationService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionRepository;
import com.kodality.termserver.terminology.valueset.ruleset.ValueSetVersionRuleSetService;
import com.kodality.termserver.terminology.valueset.snapshot.ValueSetSnapshotService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.ValueSetExternalExpandProvider;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.DesignationQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionConceptQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionConceptService {
  private final DesignationService designationService;
  private final List<ValueSetExternalExpandProvider> externalExpandProviders;
  private final ValueSetVersionConceptRepository repository;
  private final ValueSetVersionRepository valueSetVersionRepository;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ValueSetVersionRuleSetService valueSetVersionRuleSetService;
  private final ValueSetSnapshotService valueSetSnapshotService;

  @Transactional
  public List<ValueSetVersionConcept> expand(String valueSet, String valueSetVersion, ValueSetVersionRuleSet ruleSet) {
    if (valueSet == null) {
      return new ArrayList<>();
    }
    ValueSetVersion version = valueSetVersion == null ? valueSetVersionRepository.loadLastVersion(valueSet) : valueSetVersionRepository.load(valueSet, valueSetVersion);
    if (version == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> expansion = expand(version.getId(), ruleSet);

    if (version.getId() != null && ruleSet == null) {
      valueSetSnapshotService.createSnapshot(valueSet, version.getId(), expansion);
    }
    return expansion;
  }

  public List<ValueSetVersionConcept> expand(Long versionId, ValueSetVersionRuleSet ruleSet) {
    if (versionId == null) {
      return new ArrayList<>();
    }

    List<ValueSetVersionConcept> expansion = internalExpand(versionId, ruleSet);
    if (ruleSet == null) {
      ruleSet = valueSetVersionRuleSetService.load(versionId).orElse(null);
    }
    for (ValueSetExternalExpandProvider provider : externalExpandProviders) {
      expansion.addAll(provider.expand(ruleSet));
      if (ruleSet != null) {
        ruleSet.getRules().stream().filter(r -> r.getValueSetVersion() != null && r.getValueSetVersion().getId() != null)
            .forEach(r -> expansion.addAll(provider.expand(valueSetVersionRuleSetService.load(r.getValueSetVersion().getId()).orElse(null))));
      }
    }
    return expansion;
  }

  private List<ValueSetVersionConcept> internalExpand(Long versionId, ValueSetVersionRuleSet ruleSet) {
    if (versionId == null) {
      return new ArrayList<>();
    }
    if (ruleSet != null) {
      return decorate(repository.expand(versionId, ruleSet));
    }
    return decorate(repository.expand(versionId));
  }

  public List<ValueSetVersionConcept> decorate(List<ValueSetVersionConcept> concepts) {
    List<String> designationIds = new ArrayList<>();
    designationIds.addAll(concepts.stream().filter(c -> c.getDisplay() != null && c.getDisplay().getId() != null).map(c -> String.valueOf(c.getDisplay().getId())).toList());
    designationIds.addAll(concepts.stream().filter(c -> CollectionUtils.isNotEmpty(c.getAdditionalDesignations())).flatMap(c -> c.getAdditionalDesignations().stream())
        .filter(ad -> ad.getId() != null).map(ad -> String.valueOf(ad.getId())).toList());
    DesignationQueryParams designationParams = new DesignationQueryParams();
    designationParams.setId(String.join(",", designationIds));
    designationParams.setLimit(designationIds.size());
    List<Designation> designations = designationService.query(designationParams).getData();

    List<String> versionIds = concepts.stream().map(ValueSetVersionConcept::getConceptVersionId).filter(Objects::nonNull).map(String::valueOf).toList();
    CodeSystemEntityVersionQueryParams entityVersionParams = new CodeSystemEntityVersionQueryParams();
    entityVersionParams.setIds(String.join(",", versionIds));
    entityVersionParams.setStatus(String.join(",", List.of(PublicationStatus.active, PublicationStatus.draft)));
    entityVersionParams.all();
    List<CodeSystemEntityVersion> versions = CollectionUtils.isEmpty(versionIds) ? new ArrayList<>() : codeSystemEntityVersionService.query(entityVersionParams).getData();

    concepts.forEach(c -> {
      List<CodeSystemEntityVersion> conceptVersions = versions.stream().filter(v -> v.getId().equals(c.getConceptVersionId())).toList();
      c.getConcept().setVersions(conceptVersions);
      c.setDisplay(c.getDisplay() == null || c.getDisplay().getId() == null ? c.getDisplay() : designations.stream().filter(d -> d.getId().equals(c.getDisplay().getId())).findFirst().orElse(c.getDisplay()));
      c.setActive(c.isActive() || conceptVersions.stream().anyMatch(v -> PublicationStatus.active.equals(v.getStatus())));
      c.setAdditionalDesignations(CollectionUtils.isNotEmpty(c.getAdditionalDesignations()) ? c.getAdditionalDesignations().stream()
          .map(ad -> ad.getId() == null ? ad : designations.stream().filter(d -> d.getId().equals(ad.getId())).findFirst().orElse(ad))
          .collect(Collectors.toList()) : null);

      if (c.getDisplay() == null || c.getDisplay().getName() == null || CollectionUtils.isEmpty(c.getAdditionalDesignations())) {
        List<Designation> csDesignations = conceptVersions.stream().flatMap(v -> v.getDesignations() == null ?
            Stream.empty() : v.getDesignations().stream()).sorted(Comparator.comparing(d -> !d.isPreferred())).toList();
        c.setDisplay(c.getDisplay() == null || c.getDisplay().getName() == null ? csDesignations.stream().findFirst().orElse(c.getDisplay()) : c.getDisplay());
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
}
