package org.termx.modeler.structuredefinition;

import org.termx.core.http.BinaryHttpClient;
import org.termx.modeler.structuredefinition.StructureDefinition;
import org.termx.modeler.structuredefinition.StructureDefinitionVersion;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class StructureDefinitionFhirImportService {
  private final StructureDefinitionRepository repository;
  private final StructureDefinitionVersionRepository versionRepository;
  private final StructureDefinitionService structureDefinitionService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public StructureDefinition importFromUrl(String url) {
    log.info("Importing StructureDefinition from URL: {}", url);
    String json = new String(client.GET(url).body(), StandardCharsets.UTF_8);
    return importFromJson(json);
  }

  @Transactional
  public StructureDefinition importFromJson(String json) {
    StructureDefinition parsed = StructureDefinitionUtils.createStructureDefinitionFromJson(json);
    if (parsed.getUrl() == null || parsed.getUrl().isBlank()) {
      throw new IllegalArgumentException("StructureDefinition URL is required");
    }
    StructureDefinition header = repository.loadByUrl(parsed.getUrl());
    if (header == null) {
      header = new StructureDefinition()
          .setUrl(parsed.getUrl())
          .setCode(parsed.getCode() != null ? parsed.getCode() : parsed.getUrl())
          .setName(parsed.getName())
          .setParent(parsed.getParent());
      repository.save(header);
    } else {
      if (parsed.getName() != null) header.setName(parsed.getName());
      if (parsed.getParent() != null) header.setParent(parsed.getParent());
      repository.save(header);
    }
    String versionStr = parsed.getVersion();
    String fhirId = parsed.getCode();
    StructureDefinitionVersion existing = versionRepository.load(header.getId(), versionStr);
    if (existing != null) {
      existing.setContent(parsed.getContent());
      existing.setContentType(parsed.getContentType());
      existing.setContentFormat(parsed.getContentFormat());
      existing.setStatus(parsed.getStatus());
      existing.setFhirId(fhirId);
      versionRepository.save(existing);
    } else {
      StructureDefinitionVersion ver = new StructureDefinitionVersion()
          .setStructureDefinitionId(header.getId())
          .setVersion(versionStr)
          .setFhirId(fhirId)
          .setContent(parsed.getContent())
          .setContentType(parsed.getContentType() != null ? parsed.getContentType() : "Resource")
          .setContentFormat(parsed.getContentFormat() != null ? parsed.getContentFormat() : "json")
          .setStatus(parsed.getStatus() != null ? parsed.getStatus() : "draft");
      versionRepository.save(ver);
    }
    return structureDefinitionService.load(header.getId()).orElse(header);
  }
}
