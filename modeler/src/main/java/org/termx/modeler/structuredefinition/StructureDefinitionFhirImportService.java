package org.termx.modeler.structuredefinition;

import org.termx.core.http.BinaryHttpClient;
import org.termx.modeler.fhir.StructureDefinitionFhirMapper;
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
    StructureDefinition parsed = StructureDefinitionFhirMapper.fromFhir(json);
    if (parsed.getUrl() == null || parsed.getUrl().isBlank()) {
      throw new IllegalArgumentException("StructureDefinition URL is required");
    }
    StructureDefinition header = repository.loadByUrl(parsed.getUrl());
    if (header == null) {
      header = new StructureDefinition()
          .setUrl(parsed.getUrl())
          .setCode(parsed.getCode() != null ? parsed.getCode() : parsed.getUrl())
          .setName(parsed.getName())
          .setParent(parsed.getParent())
          .setPublisher(parsed.getPublisher())
          .setTitle(parsed.getTitle())
          .setDescription(parsed.getDescription())
          .setPurpose(parsed.getPurpose())
          .setCopyright(parsed.getCopyright())
          .setIdentifiers(parsed.getIdentifiers())
          .setContacts(parsed.getContacts())
          .setUseContext(parsed.getUseContext())
          .setHierarchyMeaning(parsed.getHierarchyMeaning())
          .setExperimental(parsed.getExperimental());
      repository.save(header);
    } else {
      if (parsed.getName() != null) header.setName(parsed.getName());
      if (parsed.getParent() != null) header.setParent(parsed.getParent());
      if (parsed.getPublisher() != null) header.setPublisher(parsed.getPublisher());
      if (parsed.getTitle() != null) header.setTitle(parsed.getTitle());
      if (parsed.getDescription() != null) header.setDescription(parsed.getDescription());
      if (parsed.getPurpose() != null) header.setPurpose(parsed.getPurpose());
      if (parsed.getCopyright() != null) header.setCopyright(parsed.getCopyright());
      if (parsed.getIdentifiers() != null) header.setIdentifiers(parsed.getIdentifiers());
      if (parsed.getContacts() != null) header.setContacts(parsed.getContacts());
      if (parsed.getUseContext() != null) header.setUseContext(parsed.getUseContext());
      if (parsed.getExperimental() != null) header.setExperimental(parsed.getExperimental());
      repository.save(header);
    }
    String versionStr = parsed.getVersion();
    String fhirId = parsed.getFhirId();
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
