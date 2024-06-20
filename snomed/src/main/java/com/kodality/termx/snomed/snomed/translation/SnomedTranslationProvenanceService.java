package com.kodality.termx.snomed.snomed.translation;

import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.provenance.ProvenanceUtil;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SnomedTranslationProvenanceService {
  private final ProvenanceService provenanceService;
  private final SnomedTranslationService translationService;

  public List<Provenance> find(String conceptId) {
    return provenanceService.find("Concept|" + conceptId);
  }

  public void provenanceTranslations(String action, String conceptId, Runnable save) {
    List<SnomedTranslation> before = translationService.load(conceptId);
    save.run();
    List<SnomedTranslation> after = translationService.load(conceptId);

    List<SnomedTranslation> created = after.stream().filter(a -> before.stream().noneMatch(b -> b.getId().equals(a.getId()))).toList();
    before.forEach(b -> provenanceTranslation(action, conceptId, b, after.stream().filter(a -> b.getId().equals(a.getId())).findFirst().orElse(null)));
    created.forEach(c -> provenanceTranslation(action, conceptId, null, c));
  }

  public void provenanceTranslation(String action, String conceptId, SnomedTranslation before, SnomedTranslation after) {
    Provenance provenance = new Provenance(action, "Concept", conceptId).addContext("part-of", "CodeSystem", "snomed-ct");
    if (before == null) {
      provenance.addMessage("description." + after.getDescriptionId(), String.format("created (term: %s)", after.getTerm()));
      provenanceService.create(provenance);
      return;
    }
    if (after == null) {
      provenance.addMessage("description." + before.getDescriptionId(), String.format("deleted (term: %s)", before.getTerm()));
      provenanceService.create(provenance);
      return;
    }
    Map<String, ProvenanceChange> changes = ProvenanceUtil.diff(
        Map.of("description." + before.getDescriptionId(), before),
        Map.of("description." + after.getDescriptionId(), after));
    if (CollectionUtils.isNotEmpty(changes)) {
      provenance.setChanges(changes);
      provenanceService.create(provenance);
    }
  }
}
