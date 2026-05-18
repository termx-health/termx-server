package org.termx.terminology.terminology.codesystem.entitypropertyvalue;

import com.kodality.commons.util.JsonUtil;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.codesystem.designation.DesignationService;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionRepository;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.codesystem.CodingValueUpdateCandidate;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.ConceptSnapshot;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class EntityPropertyValueCodingEnrichmentService {
  private static final int DEFAULT_BATCH_SIZE = 100;
  public static final int SYNC_RECALCULATE_THRESHOLD = 200;
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
    List<Triple<Long, Long, String>> queued = queueRepository.loadNextBatch(limit);
    if (queued.isEmpty()) {
      return false;
    }
    long started = System.currentTimeMillis();
    Map<String, List<Long>> grouped = queued.stream().collect(Collectors.groupingBy(
        t -> Optional.ofNullable(t.getRight()).orElse(""),
        Collectors.mapping(Triple::getMiddle, Collectors.toList())));
    int totalIds = 0;
    for (Map.Entry<String, List<Long>> entry : grouped.entrySet()) {
      List<String> allowedStatuses = parseStatuses(entry.getKey());
      List<Long> ids = entry.getValue().stream().distinct().toList();
      totalIds += ids.size();
      enrichCodeSystemEntityVersions(ids, allowedStatuses);
    }
    log.info("Coding enrichment batch started: queueItems={}, codeSystemEntityVersions={}", queued.size(), totalIds);
    queueRepository.complete(queued.stream().map(Triple::getLeft).toList());
    log.info("Coding enrichment batch finished: queueItems={}, codeSystemEntityVersions={}, durationMs={}",
        queued.size(), totalIds, System.currentTimeMillis() - started);
    return true;
  }

  @Transactional
  public int recalculateForEntityVersion(Long csevId, List<String> allowedStatuses) {
    return enrichCodeSystemEntityVersions(List.of(csevId), allowedStatuses);
  }

  public Map<String, Object> recalculateForCodeSystemVersion(String codeSystem, String version, List<String> allowedStatuses) {
    List<Long> csevIds = entityPropertyValueRepository.loadCodeSystemEntityVersionIdsByCsAndVersion(codeSystem, version);
    if (csevIds.isEmpty()) {
      return Map.of("applied", 0);
    }
    if (csevIds.size() <= SYNC_RECALCULATE_THRESHOLD) {
      int applied = enrichCodeSystemEntityVersions(csevIds, allowedStatuses);
      return Map.of("applied", applied);
    }
    queueRepository.markForUpdate(csevIds, serializeStatuses(allowedStatuses));
    return Map.of("queued", csevIds.size());
  }

  public List<CodingValueUpdateCandidate> findOutdatedCodingValuesForEntityVersion(Long csevId, List<String> allowedStatuses) {
    return findOutdatedCodingValues(List.of(csevId), allowedStatuses);
  }

  public List<CodingValueUpdateCandidate> findOutdatedCodingValuesForCodeSystemVersion(String codeSystem, String version, List<String> allowedStatuses) {
    List<Long> csevIds = entityPropertyValueRepository.loadCodeSystemEntityVersionIdsByCsAndVersion(codeSystem, version);
    if (csevIds.isEmpty()) {
      return List.of();
    }
    return findOutdatedCodingValues(csevIds, allowedStatuses);
  }

  private List<CodingValueUpdateCandidate> findOutdatedCodingValues(List<Long> codeSystemEntityVersionIds, List<String> allowedStatuses) {
    List<EntityPropertyValue> codingValues = entityPropertyValueRepository.loadCodingValuesByCodeSystemEntityVersionIds(codeSystemEntityVersionIds);
    if (codingValues.isEmpty()) {
      return List.of();
    }
    Map<String, Map<String, Concept>> conceptsByCodeSystem = loadTargetConcepts(codingValues);
    List<CodingValueUpdateCandidate> candidates = new ArrayList<>();
    for (EntityPropertyValue pv : codingValues) {
      EntityPropertyValueCodingValue cv = pv.asCodingValue();
      if (StringUtils.isBlank(cv.getCode()) || StringUtils.isBlank(cv.getCodeSystem())) {
        continue;
      }
      Concept concept = conceptsByCodeSystem.getOrDefault(cv.getCodeSystem(), Map.of()).get(cv.getCode());
      if (concept == null) {
        continue;
      }
      TargetSelection resolved = resolveTargetSelection(concept, allowedStatuses);
      if (resolved == null || resolved.csVersion() == null) {
        continue;
      }
      String candidateVersion = resolved.csVersion().getVersion();
      String candidateStatus = resolved.csVersion().getStatus();
      if (!StringUtils.equals(cv.getVersion(), candidateVersion)) {
        candidates.add(new CodingValueUpdateCandidate()
            .setPropertyValueId(pv.getId())
            .setCodeSystemEntityVersionId(pv.getCodeSystemEntityVersionId())
            .setEntityProperty(pv.getEntityProperty())
            .setTargetCodeSystem(cv.getCodeSystem())
            .setTargetCode(cv.getCode())
            .setCurrentVersion(cv.getVersion())
            .setCandidateVersion(candidateVersion)
            .setCandidateStatus(candidateStatus));
      }
    }
    return candidates;
  }

  protected int enrichCodeSystemEntityVersions(List<Long> codeSystemEntityVersionIds, List<String> allowedStatuses) {
    List<EntityPropertyValue> codingValues = entityPropertyValueRepository.loadCodingValuesByCodeSystemEntityVersionIds(codeSystemEntityVersionIds);
    if (codingValues.isEmpty()) {
      return 0;
    }
    Map<String, Map<String, Concept>> conceptsByCodeSystem = loadTargetConcepts(codingValues);

    List<EntityPropertyValue> changedValues = new ArrayList<>();
    for (EntityPropertyValue propertyValue : codingValues) {
      EntityPropertyValueCodingValue codingValue = propertyValue.asCodingValue();
      if (StringUtils.isBlank(codingValue.getCode()) || StringUtils.isBlank(codingValue.getCodeSystem())) {
        continue;
      }
      Concept concept = conceptsByCodeSystem.getOrDefault(codingValue.getCodeSystem(), Map.of()).get(codingValue.getCode());
      if (concept == null) {
        continue;
      }
      if (applyEnrichment(codingValue, concept, allowedStatuses)) {
        propertyValue.setValue(codingValue);
        changedValues.add(propertyValue);
      }
    }
    if (!changedValues.isEmpty()) {
      entityPropertyValueRepository.updateValues(changedValues);
      log.debug("Updated {} coding property values for {} code_system_entity_version records", changedValues.size(), codeSystemEntityVersionIds.size());
    }
    updateSnapshots(codeSystemEntityVersionIds, codingValues);
    return changedValues.size();
  }

  private Map<String, Map<String, Concept>> loadTargetConcepts(List<EntityPropertyValue> codingValues) {
    Map<String, List<EntityPropertyValue>> byCodeSystem = codingValues.stream()
        .filter(pv -> StringUtils.isNotBlank(pv.asCodingValue().getCodeSystem()))
        .collect(Collectors.groupingBy(pv -> pv.asCodingValue().getCodeSystem()));
    return byCodeSystem.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> loadConceptsByCode(e.getKey(), e.getValue())));
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

  private boolean applyEnrichment(EntityPropertyValueCodingValue codingValue, Concept concept, List<String> allowedStatuses) {
    TargetSelection resolved = resolveTargetSelection(concept, allowedStatuses);
    List<EntityPropertyValueCodingDesignationValue> display = resolved == null ? List.of() :
        Optional.ofNullable(resolved.entityVersion().getDesignations()).orElse(List.of()).stream()
            .map(this::mapDesignation)
            .toList();
    String version = resolved == null || resolved.csVersion() == null ? null : resolved.csVersion().getVersion();

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

  private record TargetSelection(CodeSystemEntityVersion entityVersion, CodeSystemVersionReference csVersion) {}

  private TargetSelection resolveTargetSelection(Concept concept, List<String> allowedStatuses) {
    if (allowedStatuses == null || allowedStatuses.isEmpty()) {
      return concept.getLastVersion()
          .map(ev -> new TargetSelection(ev,
              Optional.ofNullable(ev.getVersions()).orElse(List.of()).stream().findFirst().orElse(null)))
          .orElse(null);
    }
    return Optional.ofNullable(concept.getVersions()).orElse(List.of()).stream()
        .filter(ev -> !PublicationStatus.retired.equals(ev.getStatus()))
        .flatMap(ev -> Optional.ofNullable(ev.getVersions()).orElse(List.of()).stream()
            .map(csv -> new TargetSelection(ev, csv)))
        .filter(t -> allowedStatuses.contains(t.csVersion().getStatus()))
        .max(Comparator
            .comparing((TargetSelection t) -> t.csVersion().getReleaseDate(), Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(t -> t.csVersion().getVersion(), Comparator.nullsFirst(Comparator.naturalOrder())))
        .orElse(null);
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

  static String serializeStatuses(List<String> allowedStatuses) {
    if (allowedStatuses == null || allowedStatuses.isEmpty()) {
      return null;
    }
    return allowedStatuses.stream().filter(Objects::nonNull).distinct().collect(Collectors.joining(","));
  }

  static List<String> parseStatuses(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
