package org.termx.ucum.service;

import com.kodality.commons.model.LocalizedName;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemContent;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import org.termx.ucum.essence.UcumEssenceStorageService;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.fhir.ucum.UcumService.UcumVersionDetails;
import org.termx.ucum.dto.UcumVersionDto;
import org.termx.ucum.mapper.UcumVersionMapper;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class UcumAdministrationService {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";

  private final UcumEssenceStorageService essenceStorageService;
  private final UcumService ucumService;
  private final UcumVersionMapper ucumVersionMapper;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;

  @Transactional
  public UcumVersionDto importEssence(byte[] file) {
    UcumVersionDetails versionDetails = parseVersion(file);
    String xml = new String(file, StandardCharsets.UTF_8);
    essenceStorageService.activate(versionDetails.getVersion(), xml);
    ucumService.reload();
    ensureUcumCodeSystem();
    activateImportedVersion(versionDetails);
    return ucumVersionMapper.toDto(versionDetails);
  }

  private UcumVersionDetails parseVersion(byte[] file) {
    try {
      return new org.fhir.ucum.UcumEssenceService(new ByteArrayInputStream(file)).ucumIdentification();
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid UCUM essence XML", e);
    }
  }

  private void ensureUcumCodeSystem() {
    if (codeSystemService.load(UCUM).isPresent()) {
      return;
    }
    codeSystemService.save(new CodeSystem()
        .setId(UCUM)
        .setUri(UCUM_URI)
        .setName("UCUM")
        .setTitle(new LocalizedName(Map.of("en", "Unified Code for Units of Measure (UCUM)")))
        .setContent(CodeSystemContent.notPresent)
        .setCaseSensitive("true"));
  }

  private void activateImportedVersion(UcumVersionDetails versionDetails) {
    String version = versionDetails.getVersion();
    CodeSystemVersion imported = codeSystemVersionService.load(UCUM, version).orElseGet(() -> {
      CodeSystemVersion csVersion = new CodeSystemVersion();
      csVersion.setCodeSystem(UCUM);
      csVersion.setVersion(version);
      csVersion.setReleaseDate(LocalDate.now());
      csVersion.setPreferredLanguage("en");
      csVersion.setStatus(PublicationStatus.draft);
      codeSystemVersionService.save(csVersion);
      return csVersion;
    });
    if (!PublicationStatus.active.equals(imported.getStatus())) {
      codeSystemVersionService.activate(UCUM, version);
    }
    List<CodeSystemVersion> activeVersions = codeSystemVersionService.query(new CodeSystemVersionQueryParams()
        .setCodeSystem(UCUM)
        .setStatus(PublicationStatus.active)
        .all()).getData();
    activeVersions.stream()
        .filter(v -> !version.equals(v.getVersion()))
        .forEach(v -> codeSystemVersionService.retire(UCUM, v.getVersion()));
  }
}
