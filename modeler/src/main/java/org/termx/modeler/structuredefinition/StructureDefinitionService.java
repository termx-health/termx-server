package org.termx.modeler.structuredefinition;

import org.termx.modeler.structuredefinition.StructureDefinition;
import org.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import org.termx.modeler.structuredefinition.StructureDefinitionVersion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kodality.commons.model.QueryResult;
import org.termx.core.utils.ObjectUtil;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class StructureDefinitionService {
  private final StructureDefinitionRepository repository;
  private final StructureDefinitionVersionRepository versionRepository;
  private final StructureDefinitionContentReferenceService contentReferenceService;

  public Optional<StructureDefinition> load(Long id) {
    return load(id, null);
  }

  public Optional<StructureDefinition> load(Long id, String version) {
    StructureDefinition header = repository.load(id);
    if (header == null) return Optional.empty();
    StructureDefinitionVersion ver = version != null && !version.isBlank()
        ? versionRepository.load(id, version)
        : versionRepository.loadCurrent(id);
    return Optional.of(merge(header, ver));
  }

  public Optional<StructureDefinition> loadByUrl(String url) {
    StructureDefinition header = repository.loadByUrl(url);
    return header != null ? load(header.getId()) : Optional.empty();
  }

  public Optional<StructureDefinitionVersion> loadVersion(Long versionId) {
    return Optional.ofNullable(versionRepository.load(versionId));
  }

  public Optional<StructureDefinitionVersion> loadVersion(Long structureDefinitionId, String version) {
    return Optional.ofNullable(versionRepository.load(structureDefinitionId, version));
  }

  public List<StructureDefinitionVersion> listVersions(Long structureDefinitionId) {
    return versionRepository.listByStructureDefinition(structureDefinitionId);
  }

  public QueryResult<StructureDefinition> query(StructureDefinitionQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public StructureDefinition save(StructureDefinition structureDefinition) {
    if ("json".equals(structureDefinition.getContentFormat()) && structureDefinition.getContent() != null) {
      try {
        structureDefinition.setContent(ObjectUtil.removeEmptyAttributes(structureDefinition.getContent()));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
    repository.save(structureDefinition);
    Long headerId = structureDefinition.getId();
    StructureDefinitionVersion ver = structureDefinition.getVersion() != null && !structureDefinition.getVersion().isBlank()
        ? versionRepository.load(headerId, structureDefinition.getVersion())
        : null;
    if (ver == null) {
      ver = new StructureDefinitionVersion()
          .setStructureDefinitionId(headerId)
          .setVersion(structureDefinition.getVersion())
          .setFhirId(structureDefinition.getFhirId())
          .setContent(structureDefinition.getContent())
          .setContentType(structureDefinition.getContentType())
          .setContentFormat(structureDefinition.getContentFormat() != null ? structureDefinition.getContentFormat() : "json")
          .setStatus(structureDefinition.getStatus() != null ? structureDefinition.getStatus() : "draft")
          .setReleaseDate(structureDefinition.getReleaseDate());
      versionRepository.save(ver);
      contentReferenceService.extractAndSave(ver.getId(), ver.getContent(), ver.getContentFormat());
    } else {
      ver.setContent(structureDefinition.getContent());
      ver.setContentType(structureDefinition.getContentType());
      ver.setContentFormat(structureDefinition.getContentFormat());
      ver.setStatus(structureDefinition.getStatus());
      ver.setReleaseDate(structureDefinition.getReleaseDate());
      ver.setFhirId(structureDefinition.getFhirId());
      versionRepository.save(ver);
      contentReferenceService.extractAndSave(ver.getId(), ver.getContent(), ver.getContentFormat());
    }
    return load(headerId).orElse(null);
  }

  @Transactional
  public StructureDefinitionVersion saveVersion(StructureDefinitionVersion version) {
    if ("json".equals(version.getContentFormat()) && version.getContent() != null) {
      try {
        version.setContent(ObjectUtil.removeEmptyAttributes(version.getContent()));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
    versionRepository.save(version);
    contentReferenceService.extractAndSave(version.getId(), version.getContent(), version.getContentFormat());
    return version;
  }

  @Transactional
  public StructureDefinitionVersion duplicateVersion(Long structureDefinitionId, String sourceVersion, String targetVersion) {
    StructureDefinitionVersion source = versionRepository.load(structureDefinitionId, sourceVersion);
    if (source == null) {
      throw new com.kodality.commons.exception.NotFoundException("Source version not found: " + structureDefinitionId + "/" + sourceVersion);
    }
    StructureDefinitionVersion target = new StructureDefinitionVersion()
        .setStructureDefinitionId(structureDefinitionId)
        .setVersion(targetVersion)
        .setContent(source.getContent())
        .setContentType(source.getContentType())
        .setContentFormat(source.getContentFormat())
        .setStatus("draft");
    versionRepository.save(target);
    contentReferenceService.extractAndSave(target.getId(), target.getContent(), target.getContentFormat());
    return target;
  }

  @Transactional
  public void cancel(Long id) {
    for (StructureDefinitionVersion v : versionRepository.listByStructureDefinition(id)) {
      versionRepository.cancel(v.getId());
    }
    repository.cancel(id);
  }

  private static StructureDefinition merge(StructureDefinition header, StructureDefinitionVersion ver) {
    if (header == null) return null;
    header.setContent(ver != null ? ver.getContent() : null);
    header.setContentType(ver != null ? ver.getContentType() : null);
    header.setContentFormat(ver != null ? ver.getContentFormat() : null);
    header.setVersion(ver != null ? ver.getVersion() : null);
    header.setFhirId(ver != null ? ver.getFhirId() : null);
    header.setStatus(ver != null ? ver.getStatus() : null);
    header.setReleaseDate(ver != null ? ver.getReleaseDate() : null);
    return header;
  }
}
