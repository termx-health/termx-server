package org.termx.snomed.integration.rf2.scan;

import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.termx.snomed.integration.rf2.scan.SnomedRF2ZipParser.ConceptRow;
import org.termx.snomed.integration.rf2.scan.SnomedRF2ZipParser.DescriptionRow;
import org.termx.snomed.integration.rf2.scan.SnomedRF2ZipParser.LanguageRefsetRow;
import org.termx.snomed.integration.rf2.scan.SnomedRF2ZipParser.ParsedRF2;
import org.termx.snomed.integration.rf2.scan.SnomedRF2ZipParser.RelationshipRow;
import org.termx.snomed.rf2.scan.SnomedRF2Attribute;
import org.termx.snomed.rf2.scan.SnomedRF2Designation;
import org.termx.snomed.rf2.scan.SnomedRF2InvalidatedConcept;
import org.termx.snomed.rf2.scan.SnomedRF2ModifiedConcept;
import org.termx.snomed.rf2.scan.SnomedRF2NewConcept;
import org.termx.snomed.rf2.scan.SnomedRF2ScanResult;

@Singleton
public class SnomedRF2DiffEngine {

  private static final String FSN = "900000000000003001";
  private static final String SYNONYM = "900000000000013009";
  private static final String TEXT_DEFINITION = "900000000000550004";
  private static final String PREFERRED = "900000000000548007";
  private static final String ACCEPTABLE = "900000000000549004";

  public SnomedRF2ScanResult classify(ParsedRF2 parsed, String branchPath, String rf2Type) {
    String releaseEffectiveTime = computeReleaseEffectiveTime(parsed);

    List<ConceptRow> concepts = changesetConcepts(parsed.getConcepts(), releaseEffectiveTime);
    List<DescriptionRow> descriptions = changesetDescriptions(parsed.getDescriptions(), releaseEffectiveTime);
    List<RelationshipRow> relationships = changesetRelationships(parsed.getRelationships(), releaseEffectiveTime);

    Map<String, String> acceptabilityByDescription = buildAcceptabilityIndex(parsed.getLanguageRefset(), releaseEffectiveTime);

    Map<String, ConceptRow> conceptByCode = concepts.stream()
        .collect(Collectors.toMap(ConceptRow::getId, c -> c, (a, b) -> b, LinkedHashMap::new));

    Map<String, List<DescriptionRow>> descriptionsByConcept = descriptions.stream()
        .collect(Collectors.groupingBy(DescriptionRow::getConceptId, LinkedHashMap::new, Collectors.toList()));

    Map<String, List<RelationshipRow>> relationshipsBySource = relationships.stream()
        .collect(Collectors.groupingBy(RelationshipRow::getSourceId, LinkedHashMap::new, Collectors.toList()));

    java.util.Set<String> touchedConceptIds = new java.util.LinkedHashSet<>();
    touchedConceptIds.addAll(conceptByCode.keySet());
    touchedConceptIds.addAll(descriptionsByConcept.keySet());
    touchedConceptIds.addAll(relationshipsBySource.keySet());

    List<SnomedRF2NewConcept> newConcepts = new ArrayList<>();
    List<SnomedRF2ModifiedConcept> modifiedConcepts = new ArrayList<>();
    List<SnomedRF2InvalidatedConcept> invalidatedConcepts = new ArrayList<>();

    for (String conceptId : touchedConceptIds) {
      ConceptRow conceptRow = conceptByCode.get(conceptId);
      List<DescriptionRow> conceptDescriptions = descriptionsByConcept.getOrDefault(conceptId, List.of());
      List<RelationshipRow> conceptRelationships = relationshipsBySource.getOrDefault(conceptId, List.of());

      if (conceptRow != null && !conceptRow.isActive()) {
        invalidatedConcepts.add(toInvalidated(conceptRow, conceptDescriptions, acceptabilityByDescription));
      } else if (conceptRow != null) {
        newConcepts.add(toNew(conceptRow, conceptDescriptions, conceptRelationships, acceptabilityByDescription));
      } else {
        modifiedConcepts.add(toModified(conceptId, conceptDescriptions, conceptRelationships, acceptabilityByDescription));
      }
    }

    SnomedRF2ScanResult.Stats stats = new SnomedRF2ScanResult.Stats()
        .setConceptsAdded(newConcepts.size())
        .setConceptsModified(modifiedConcepts.size())
        .setConceptsInvalidated(invalidatedConcepts.size())
        .setDescriptionsAdded((int) descriptions.stream().filter(DescriptionRow::isActive).count())
        .setDescriptionsInvalidated((int) descriptions.stream().filter(d -> !d.isActive()).count())
        .setRelationshipsAdded((int) relationships.stream().filter(RelationshipRow::isActive).count())
        .setRelationshipsInvalidated((int) relationships.stream().filter(r -> !r.isActive()).count());

    return new SnomedRF2ScanResult()
        .setBranchPath(branchPath)
        .setRf2Type(rf2Type)
        .setReleaseEffectiveTime(releaseEffectiveTime)
        .setScannedAt(OffsetDateTime.now())
        .setStats(stats)
        .setNewConcepts(newConcepts)
        .setModifiedConcepts(modifiedConcepts)
        .setInvalidatedConcepts(invalidatedConcepts);
  }

  private String computeReleaseEffectiveTime(ParsedRF2 parsed) {
    Comparator<String> cmp = Comparator.nullsFirst(Comparator.naturalOrder());
    return parsed.getConcepts().stream()
        .map(ConceptRow::getEffectiveTime)
        .filter(s -> s != null && !s.isEmpty())
        .max(cmp)
        .orElseGet(() -> parsed.getDescriptions().stream()
            .map(DescriptionRow::getEffectiveTime)
            .filter(s -> s != null && !s.isEmpty())
            .max(cmp)
            .orElse(null));
  }

  private List<ConceptRow> changesetConcepts(List<ConceptRow> all, String release) {
    Map<String, ConceptRow> latest = new HashMap<>();
    for (ConceptRow row : all) {
      if (release == null || release.equals(emptyAsNull(row.getEffectiveTime())) || emptyAsNull(row.getEffectiveTime()) == null) {
        ConceptRow existing = latest.get(row.getId());
        if (existing == null || compare(existing.getEffectiveTime(), row.getEffectiveTime()) <= 0) {
          latest.put(row.getId(), row);
        }
      }
    }
    return new ArrayList<>(latest.values());
  }

  private List<DescriptionRow> changesetDescriptions(List<DescriptionRow> all, String release) {
    Map<String, DescriptionRow> latest = new HashMap<>();
    for (DescriptionRow row : all) {
      if (release == null || release.equals(emptyAsNull(row.getEffectiveTime())) || emptyAsNull(row.getEffectiveTime()) == null) {
        DescriptionRow existing = latest.get(row.getId());
        if (existing == null || compare(existing.getEffectiveTime(), row.getEffectiveTime()) <= 0) {
          latest.put(row.getId(), row);
        }
      }
    }
    return new ArrayList<>(latest.values());
  }

  private List<RelationshipRow> changesetRelationships(List<RelationshipRow> all, String release) {
    Map<String, RelationshipRow> latest = new HashMap<>();
    for (RelationshipRow row : all) {
      if (release == null || release.equals(emptyAsNull(row.getEffectiveTime())) || emptyAsNull(row.getEffectiveTime()) == null) {
        RelationshipRow existing = latest.get(row.getId());
        if (existing == null || compare(existing.getEffectiveTime(), row.getEffectiveTime()) <= 0) {
          latest.put(row.getId(), row);
        }
      }
    }
    return new ArrayList<>(latest.values());
  }

  private Map<String, String> buildAcceptabilityIndex(List<LanguageRefsetRow> rows, String release) {
    Map<String, String> latestByDescription = new HashMap<>();
    Map<String, String> latestEffectiveTime = new HashMap<>();
    for (LanguageRefsetRow row : rows) {
      if (!row.isActive()) {
        continue;
      }
      String existingEt = latestEffectiveTime.get(row.getReferencedComponentId());
      if (existingEt == null || compare(existingEt, row.getEffectiveTime()) <= 0) {
        latestEffectiveTime.put(row.getReferencedComponentId(), row.getEffectiveTime());
        latestByDescription.put(row.getReferencedComponentId(), mapAcceptability(row.getAcceptabilityId()));
      }
    }
    return latestByDescription;
  }

  private SnomedRF2NewConcept toNew(ConceptRow row, List<DescriptionRow> descriptions, List<RelationshipRow> relationships, Map<String, String> acceptability) {
    return new SnomedRF2NewConcept()
        .setConceptId(row.getId())
        .setEffectiveTime(row.getEffectiveTime())
        .setModuleId(row.getModuleId())
        .setDefinitionStatusId(row.getDefinitionStatusId())
        .setDesignations(descriptions.stream().filter(DescriptionRow::isActive).map(d -> toDesignation(d, acceptability)).toList())
        .setAttributes(relationships.stream().filter(RelationshipRow::isActive).map(this::toAttribute).toList());
  }

  private SnomedRF2ModifiedConcept toModified(String conceptId, List<DescriptionRow> descriptions, List<RelationshipRow> relationships, Map<String, String> acceptability) {
    List<SnomedRF2Designation> added = descriptions.stream().filter(DescriptionRow::isActive).map(d -> toDesignation(d, acceptability)).toList();
    List<SnomedRF2Designation> removed = descriptions.stream().filter(d -> !d.isActive()).map(d -> toDesignation(d, acceptability)).toList();
    List<SnomedRF2Attribute> addedAttr = relationships.stream().filter(RelationshipRow::isActive).map(this::toAttribute).toList();
    List<SnomedRF2Attribute> removedAttr = relationships.stream().filter(r -> !r.isActive()).map(this::toAttribute).toList();
    return new SnomedRF2ModifiedConcept()
        .setConceptId(conceptId)
        .setAddedDesignations(added)
        .setRemovedDesignations(removed)
        .setAddedAttributes(addedAttr)
        .setRemovedAttributes(removedAttr);
  }

  private SnomedRF2InvalidatedConcept toInvalidated(ConceptRow row, List<DescriptionRow> descriptions, Map<String, String> acceptability) {
    return new SnomedRF2InvalidatedConcept()
        .setConceptId(row.getId())
        .setEffectiveTime(row.getEffectiveTime())
        .setModuleId(row.getModuleId())
        .setDesignations(descriptions.stream().map(d -> toDesignation(d, acceptability)).toList());
  }

  private SnomedRF2Designation toDesignation(DescriptionRow row, Map<String, String> acceptability) {
    return new SnomedRF2Designation()
        .setDescriptionId(row.getId())
        .setTerm(row.getTerm())
        .setType(mapDescriptionType(row))
        .setLanguage(row.getLanguageCode())
        .setAcceptability(Optional.ofNullable(acceptability.get(row.getId())).orElse("none"))
        .setActive(row.isActive())
        .setEffectiveTime(row.getEffectiveTime());
  }

  private SnomedRF2Attribute toAttribute(RelationshipRow row) {
    return new SnomedRF2Attribute()
        .setRelationshipId(row.getId())
        .setTypeId(row.getTypeId())
        .setDestinationId(row.getDestinationId())
        .setRelationshipGroup(row.getRelationshipGroup())
        .setCharacteristicTypeId(row.getCharacteristicTypeId())
        .setModifierId(row.getModifierId())
        .setActive(row.isActive())
        .setEffectiveTime(row.getEffectiveTime());
  }

  private String mapDescriptionType(DescriptionRow row) {
    if (row.isTextDefinition() || TEXT_DEFINITION.equals(row.getTypeId())) {
      return "textDefinition";
    }
    if (FSN.equals(row.getTypeId())) {
      return "fully-specified-name";
    }
    if (SYNONYM.equals(row.getTypeId())) {
      return "synonym";
    }
    return Optional.ofNullable(row.getTypeId()).orElse("unknown");
  }

  private String mapAcceptability(String acceptabilityId) {
    if (PREFERRED.equals(acceptabilityId)) {
      return "preferred";
    }
    if (ACCEPTABLE.equals(acceptabilityId)) {
      return "acceptable";
    }
    return Optional.ofNullable(acceptabilityId).orElse("none");
  }

  private static String emptyAsNull(String s) {
    return (s == null || s.isEmpty()) ? null : s;
  }

  private static int compare(String a, String b) {
    String x = emptyAsNull(a);
    String y = emptyAsNull(b);
    if (Objects.equals(x, y)) {
      return 0;
    }
    if (x == null) {
      return -1;
    }
    if (y == null) {
      return 1;
    }
    return x.compareTo(y);
  }
}
