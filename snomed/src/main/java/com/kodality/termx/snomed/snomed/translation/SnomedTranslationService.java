package com.kodality.termx.snomed.snomed.translation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.concept.SnomedTranslationSearchParams;
import com.kodality.termx.snomed.concept.SnomedTranslationStatus;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.snomed.SnomedInterceptor;
import com.kodality.termx.snomed.snomed.SnomedService;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class SnomedTranslationService {
  private final SnomedService snomedService;
  private final SnomedTranslationRepository repository;
  private final SnomedInterceptor snomedInterceptor;

  @Value("${snowstorm.namespace}")
  private Optional<String> snowstormNamespace;

  public static final String DESCRIPTION_PARTITION_IDENTIFIER = "11";

  public List<SnomedTranslation> load(String conceptId) {
    return repository.load(conceptId);
  }

  public SnomedTranslation load(Long id) {
    return repository.load(id);
  }

  public List<SnomedTranslation> loadAll(boolean onlyActive, boolean unlinked, String branch) {
    SnomedTranslationSearchParams params =
        new SnomedTranslationSearchParams().setStatus(onlyActive ? SnomedTranslationStatus.active : null).setBranch(branch).all();
    List<SnomedTranslation> translations = query(params).getData();

    if (CollectionUtils.isNotEmpty(translations) && unlinked) {
      List<String> conceptIds = translations.stream().map(SnomedTranslation::getConceptId).distinct().toList();
      List<SnomedDescription> snomedDescriptions = snomedService.loadDescriptions(branch, conceptIds);
      translations =
          translations.stream().filter(t -> snomedDescriptions.stream().noneMatch(sd -> sd.getDescriptionId().equals(t.getDescriptionId()))).toList();
    }
    return translations;
  }

  public QueryResult<SnomedTranslation> query(SnomedTranslationSearchParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(String conceptId, List<SnomedTranslation> translations) {
    List<Long> cancelledTranslations = repository.retain(conceptId, translations);
    snomedInterceptor.afterTranslationsCancel(cancelledTranslations);
    if (CollectionUtils.isNotEmpty(translations)) {
      translations.forEach(t -> save(conceptId, t));
    }
  }

  private void save(String conceptId, SnomedTranslation t) {
    prepare(t);
    repository.save(conceptId, t);
    if (t.getDescriptionId() == null) {
      saveDescription(t);
    }
    snomedInterceptor.afterTranslationSave(conceptId, t);
  }

  private void prepare(SnomedTranslation t) {
    t.setStatus(t.getStatus() == null ? SnomedTranslationStatus.proposed : t.getStatus());
  }

  private void saveDescription(SnomedTranslation t) {
    try {
      repository.saveDescriptionId(t.getId(), composeSCTID(t.getId()));
    } catch (CheckDigitException e) {
      e.printStackTrace();
    }
  }

  private String composeSCTID(Long id) throws CheckDigitException {
    String namespace = snowstormNamespace.orElseThrow(() -> new RuntimeException("snowstorm namespace identifier is not configured"));
    VerhoeffCheckDigit verhoeffCheckDigit = new VerhoeffCheckDigit();
    String checkDigit = verhoeffCheckDigit.calculate(id + namespace + DESCRIPTION_PARTITION_IDENTIFIER);
    return id + snowstormNamespace.get() + DESCRIPTION_PARTITION_IDENTIFIER + checkDigit;
  }
}
