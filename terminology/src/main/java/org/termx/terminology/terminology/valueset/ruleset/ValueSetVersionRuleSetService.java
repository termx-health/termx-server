package org.termx.terminology.terminology.valueset.ruleset;

import com.kodality.commons.exception.NotFoundException;
import org.termx.terminology.ApiError;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionRepository;
import org.termx.ts.PublicationStatus;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionRuleSet;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionRuleSetService {
  private final ValueSetVersionRuleSetRepository repository;
  private final ValueSetVersionRepository valueSetVersionRepository;
  private final ValueSetVersionRuleRepository valueSetVersionRuleRepository;

  public Optional<ValueSetVersionRuleSet> load(String valueSet, String valueSetVersion) {
    return Optional.ofNullable(repository.load(valueSet, valueSetVersion));
  }

  public Optional<ValueSetVersionRuleSet> load(Long valueSetVersionId) {
    return Optional.ofNullable(decorate(repository.load(valueSetVersionId)));
  }

  private ValueSetVersionRuleSet decorate(ValueSetVersionRuleSet ruleSet) {
    if (ruleSet != null) {
      ruleSet.setRules(valueSetVersionRuleRepository.loadAll(ruleSet.getId()));
    }
    return ruleSet;
  }

  @Transactional
  public ValueSetVersionRuleSet save(ValueSetVersionRuleSet ruleSet, String valueSet, String valueSetVersion) {
    Long versionId = requireDraft(valueSet, valueSetVersion).getId();
    requireOwn(ruleSet, versionId, valueSet, valueSetVersion);
    repository.save(ruleSet, versionId);
    return ruleSet;
  }

  // a rule set id from the request must be the one of the version named in the path: saving under another
  // version's id would move that rule set, with all its rules, to this version
  private void requireOwn(ValueSetVersionRuleSet ruleSet, Long versionId, String valueSet, String valueSetVersion) {
    if (ruleSet.getId() == null) {
      return;
    }
    ValueSetVersionRuleSet own = repository.load(versionId);
    if (own == null || !ruleSet.getId().equals(own.getId())) {
      throw ApiError.TE124.toApiException(Map.of("id", ruleSet.getId(), "valueSet", valueSet, "version", valueSetVersion));
    }
  }

  /** Rules are expanded when a version is published; afterwards they would no longer match its expansion. */
  public ValueSetVersion requireDraft(String valueSet, String valueSetVersion) {
    ValueSetVersion version = valueSetVersionRepository.load(valueSet, valueSetVersion);
    if (version == null) {
      throw new NotFoundException("ValueSetVersion", valueSetVersion);
    }
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      throw ApiError.TE122.toApiException(Map.of("valueSet", valueSet, "version", valueSetVersion));
    }
    return version;
  }
}
