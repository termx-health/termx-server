package org.termx.modeler.structuredefinition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.termx.terminology.fhir.FhirFshConverter;
import org.termx.terminology.terminology.valueset.ValueSetRepository;
import org.termx.ts.valueset.ValueSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class StructureDefinitionContentReferenceService implements StructureDefinitionContentReferenceProvider {
  private final StructureDefinitionContentReferenceRepository repository;
  private final StructureDefinitionRepository structureDefinitionRepository;
  private final StructureDefinitionVersionRepository versionRepository;
  private final ValueSetRepository valueSetRepository;
  private final Optional<FhirFshConverter> fhirFshConverter;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final List<String> STANDARD_PREFIXES = List.of(
      "http://hl7.org/fhir",
      "https://hl7.org/fhir",
      "http://terminology.hl7.org",
      "https://terminology.hl7.org"
  );

  @Transactional
  public void extractAndSave(Long versionId, String content, String contentFormat) {
    repository.deleteByVersionId(versionId);
    if (content == null || content.isBlank()) {
      return;
    }

    String fhirJson = toFhirJson(content, contentFormat);
    if (fhirJson == null) {
      return;
    }

    List<String> urls;
    try {
      JsonNode root = MAPPER.readTree(fhirJson);
      urls = extractUrls(root);
    } catch (Exception e) {
      log.warn("Failed to parse StructureDefinition content for reference extraction (versionId={}): {}", versionId, e.getMessage());
      return;
    }

    Set<String> seen = new HashSet<>();
    for (String rawUrl : urls) {
      String normalized = normalizeUrl(rawUrl);
      if (normalized.isBlank() || isStandardUrl(normalized) || !seen.add(normalized)) {
        continue;
      }
      StructureDefinitionContentReference ref = resolve(normalized);
      ref.setStructureDefinitionVersionId(versionId);
      repository.save(ref);
    }
  }

  @Transactional
  public void recalculate(Long structureDefinitionId) {
    List<StructureDefinitionVersion> versions = versionRepository.listByStructureDefinition(structureDefinitionId);
    for (StructureDefinitionVersion version : versions) {
      extractAndSave(version.getId(), version.getContent(), version.getContentFormat());
    }
  }

  @Override
  public List<StructureDefinitionContentReference> findByResourceTypeAndResourceId(String resourceType, String resourceId) {
    return repository.loadByResourceTypeAndResourceId(resourceType, resourceId);
  }

  @Override
  public List<Long> findReferencingStructureDefinitionIds(String resourceType, String resourceId) {
    return repository.findDistinctStructureDefinitionIds(resourceType, resourceId);
  }

  // -- URL extraction --

  private List<String> extractUrls(JsonNode root) {
    List<String> urls = new ArrayList<>();
    JsonNode baseDefinition = root.get("baseDefinition");
    if (baseDefinition != null && baseDefinition.isTextual()) {
      urls.add(baseDefinition.asText());
    }
    extractFromElements(root.path("snapshot").path("element"), urls);
    extractFromElements(root.path("differential").path("element"), urls);
    return urls;
  }

  private void extractFromElements(JsonNode elements, List<String> urls) {
    if (!elements.isArray()) {
      return;
    }
    for (JsonNode element : elements) {
      JsonNode types = element.path("type");
      if (types.isArray()) {
        for (JsonNode type : types) {
          addAllTextValues(type.path("profile"), urls);
          addAllTextValues(type.path("targetProfile"), urls);
        }
      }
      JsonNode valueSet = element.path("binding").path("valueSet");
      if (valueSet.isTextual()) {
        urls.add(valueSet.asText());
      }
    }
  }

  private void addAllTextValues(JsonNode arrayNode, List<String> urls) {
    if (arrayNode.isArray()) {
      for (JsonNode item : arrayNode) {
        if (item.isTextual()) {
          urls.add(item.asText());
        }
      }
    }
  }

  // -- URL normalization and filtering --

  private String normalizeUrl(String url) {
    String normalized = url.trim();
    int pipeIndex = normalized.indexOf('|');
    if (pipeIndex > 0) {
      normalized = normalized.substring(0, pipeIndex);
    }
    return normalized.toLowerCase();
  }

  private boolean isStandardUrl(String normalizedUrl) {
    return STANDARD_PREFIXES.stream().anyMatch(prefix -> normalizedUrl.startsWith(prefix.toLowerCase()));
  }

  // -- Resolution --

  private StructureDefinitionContentReference resolve(String normalizedUrl) {
    StructureDefinitionContentReference ref = new StructureDefinitionContentReference().setUrl(normalizedUrl);

    StructureDefinition sd = structureDefinitionRepository.loadByUrlIgnoreCase(normalizedUrl);
    if (sd != null) {
      return ref.setResourceType("StructureDefinition").setResourceId(String.valueOf(sd.getId()));
    }

    ValueSet vs = valueSetRepository.loadByUriIgnoreCase(normalizedUrl);
    if (vs != null) {
      return ref.setResourceType("ValueSet").setResourceId(vs.getId());
    }

    return ref;
  }

  // -- FSH conversion --

  private String toFhirJson(String content, String contentFormat) {
    if ("json".equals(contentFormat)) {
      return content;
    }
    if ("fsh".equals(contentFormat)) {
      if (fhirFshConverter.isEmpty()) {
        log.warn("FhirFshConverter is not available. Skipping reference extraction for FSH content.");
        return null;
      }
      try {
        return fhirFshConverter.get().toFhir(content).join();
      } catch (Exception e) {
        log.warn("Failed to convert FSH to FHIR JSON for reference extraction: {}", e.getMessage());
        return null;
      }
    }
    return null;
  }
}
