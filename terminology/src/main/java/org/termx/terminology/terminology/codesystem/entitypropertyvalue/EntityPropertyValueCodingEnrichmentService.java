package org.termx.terminology.terminology.codesystem.entitypropertyvalue;

import com.kodality.commons.util.JsonUtil;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.codesystem.designation.DesignationService;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionRepository;
import org.termx.ts.codesystem.ConceptSnapshot;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.DesignationQueryParams;
import org.termx.ts.codesystem.EntityPropertyValue;
import org.termx.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingDesignationValue;
import org.termx.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class EntityPropertyValueCodingEnrichmentService {
  private static final int DEFAULT_BATCH_SIZE = 100;
  private final AtomicBoolean running = new AtomicBoolean(false);

  private final EntityPropertyValueUpdateQueueRepository queueRepository;
  private final EntityPropertyValueRepository entityPropertyValueRepository;
  private final CodeSystemEntityVersionRepository codeSystemEntityVersionRepository;
  private final DesignationService designationService;
  private final ConceptService conceptService;

  public void processNextBatch() {
    processNextBatch(DEFAULT_BATCH_SIZE);
  }

  public void processNextBatch(int limit) {
    if (!running.compareAndSet(false, true)) {
      log.debug("Skipping coding enrichment run, previous run is still in progress");
      return;
    }
    try {
      while (processSingleBatch(limit)) {
        // loop until queue is empty
      }
    } finally {
      running.set(false);
    }
  }

  @Transactional
  protected boolean processSingleBatch(int limit) {
    List<Pair<Long, Long>> queued = queueRepository.loadNextBatch(limit);
    if (queued.isEmpty()) {
      return false;
    }
    long started = System.currentTimeMillis();
    List<Long> codeSystemEntityVersionIds = queued.stream().map(Pair::getRight).distinct().toList();
    log.info("Coding enrichment batch started: queueItems={}, codeSystemEntityVersions={}", queued.size(), codeSystemEntityVersionIds.size());
    enrichCodeSystemEntityVersions(codeSystemEntityVersionIds);
    queueRepository.complete(queued.stream().map(Pair::getLeft).toList());
    log.info("Coding enrichment batch finished: queueItems={}, codeSystemEntityVersions={}, durationMs={}",
        queued.size(), codeSystemEntityVersionIds.size(), System.currentTimeMillis() - started);
    return true;
  }

  private void enrichCodeSystemEntityVersions(List<Long> codeSystemEntityVersionIds) {
    List<EntityPropertyValue> codingValues = entityPropertyValueRepository.loadCodingValuesByCodeSystemEntityVersionIds(codeSystemEntityVersionIds);
    if (codingValues.isEmpty()) {
      return;
    }

    Map<String, List<EntityPropertyValue>> byCodeSystem = codingValues.stream()
        .filter(pv -> StringUtils.isNotBlank(pv.asCodingValue().getCodeSystem()))
        .collect(Collectors.groupingBy(pv -> pv.asCodingValue().getCodeSystem()));

    Map<String, Map<String, Concept>> conceptsByCodeSystem = byCodeSystem.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> loadConceptsByCode(e.getKey(), e.getValue())));

    List<EntityPropertyValue> changedValues = new ArrayList<>();
    for (Map.Entry<String, List<EntityPropertyValue>> entry : byCodeSystem.entrySet()) {
      Map<String, Concept> conceptsByCode = conceptsByCodeSystem.getOrDefault(entry.getKey(), Map.of());
      for (EntityPropertyValue propertyValue : entry.getValue()) {
        EntityPropertyValueCodingValue codingValue = propertyValue.asCodingValue();
        if (StringUtils.isBlank(codingValue.getCode())) {
          continue;
        }
        Concept concept = conceptsByCode.get(codingValue.getCode());
        if (concept == null) {
          continue;
        }
        if (applyEnrichment(codingValue, concept)) {
          propertyValue.setValue(codingValue);
          changedValues.add(propertyValue);
        }
      }
    }
    if (!changedValues.isEmpty()) {
      entityPropertyValueRepository.updateValues(changedValues);
      log.debug("Updated {} coding property values for {} code_system_entity_version records", changedValues.size(), codeSystemEntityVersionIds.size());
    }
    updateSnapshots(codeSystemEntityVersionIds, codingValues);
  }

  private Map<String, Concept> loadConceptsByCode(String codeSystem, List<EntityPropertyValue> values) {
    List<String> codes = values.stream()
        .map(EntityPropertyValue::asCodingValue)
        .map(EntityPropertyValueCodingValue::getCode)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .toList();
    if (codes.isEmpty()) {
      return Map.of();
    }
    ConceptQueryParams params = new ConceptQueryParams()
        .setCodeSystem(codeSystem)
        .setCodes(codes);
    params.setLimit(Math.max(10_000, codes.size()));
    return conceptService.query(params).getData().stream()
        .collect(Collectors.toMap(Concept::getCode, c -> c, (left, right) -> left));
  }

  private boolean applyEnrichment(EntityPropertyValueCodingValue codingValue, Concept concept) {
    List<EntityPropertyValueCodingDesignationValue> display = concept.getLastVersion()
        .map(CodeSystemEntityVersion::getDesignations)
        .orElse(List.of())
        .stream()
        .map(this::mapDesignation)
        .toList();
    String version = concept.getLastVersion()
        .map(CodeSystemEntityVersion::getVersions)
        .filter(v -> v != null && !v.isEmpty())
        .flatMap(v -> v.stream().findFirst())
        .map(CodeSystemVersionReference::getVersion)
        .orElse(null);

    boolean changed = false;
    if (!StringUtils.equals(JsonUtil.toJson(codingValue.getDisplay()), JsonUtil.toJson(display))) {
      codingValue.setDisplay(display);
      changed = true;
    }
    if (!StringUtils.equals(codingValue.getVersion(), version)) {
      codingValue.setVersion(version);
      changed = true;
    }
    return changed;
  }

  private EntityPropertyValueCodingDesignationValue mapDesignation(Designation designation) {
    return new EntityPropertyValueCodingDesignationValue()
        .setName(designation.getName())
        .setLanguage(designation.getLanguage())
        .setUse(designation.getDesignationType());
  }

  private void updateSnapshots(List<Long> codeSystemEntityVersionIds, List<EntityPropertyValue> codingValues) {
    String ids = codeSystemEntityVersionIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    DesignationQueryParams p = new DesignationQueryParams().setCodeSystemEntityVersionId(ids);
    p.setLimit(-1);
    Map<Long, List<Designation>> designationsByVersion = designationService.query(p).getData().stream()
        .collect(Collectors.groupingBy(Designation::getCodeSystemEntityVersionId));
    Map<Long, List<EntityPropertyValue>> valuesByVersion = codingValues.stream()
        .collect(Collectors.groupingBy(EntityPropertyValue::getCodeSystemEntityVersionId));

    Map<Long, ConceptSnapshot> snapshots = codeSystemEntityVersionIds.stream()
        .filter(id -> valuesByVersion.containsKey(id))
        .collect(Collectors.toMap(id -> id, id -> new ConceptSnapshot()
            .setDesignation(mapSnapshotDesignations(designationsByVersion.getOrDefault(id, List.of())))
            .setProperties(mapSnapshotProperties(valuesByVersion.getOrDefault(id, List.of())))));
    codeSystemEntityVersionRepository.updateSnapshots(snapshots);
  }

  private List<ConceptSnapshot.SnapshotDesignation> mapSnapshotDesignations(List<Designation> designations) {
    return designations.stream()
        .map(d -> new ConceptSnapshot.SnapshotDesignation(d.getDesignationType(), d.getLanguage(), d.getName()))
        .sorted(Comparator
            .comparing(ConceptSnapshot.SnapshotDesignation::language, Comparator.nullsLast(String::compareTo))
            .thenComparing(ConceptSnapshot.SnapshotDesignation::use, Comparator.nullsLast(String::compareTo))
            .thenComparing(ConceptSnapshot.SnapshotDesignation::name, Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  private List<ConceptSnapshot.SnapshotProperty> mapSnapshotProperties(List<EntityPropertyValue> values) {
    return values.stream()
        .map(this::mapSnapshotProperty)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(ConceptSnapshot.SnapshotProperty::code, Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  private ConceptSnapshot.SnapshotProperty mapSnapshotProperty(EntityPropertyValue propertyValue) {
    EntityPropertyValueCodingValue coding = propertyValue.asCodingValue();
    return new ConceptSnapshot.SnapshotProperty(
        propertyValue.getEntityProperty(),
        new ConceptSnapshot.SnapshotCoding(
            coding.getCodeSystem(),
            coding.getVersion(),
            coding.getCode(),
            coding.getDisplay() == null ? null : JsonUtil.toJson(coding.getDisplay())
        ),
        null,
        null
    );
  }
}
