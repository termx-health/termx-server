package com.kodality.termx.terminology.codesystem;


import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.fhir.FhirFshConverter;
import com.kodality.termx.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termx.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataCodeSystemHandler implements SpaceGithubDataHandler {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ProvenanceService provenanceService;
  private final CodeSystemFhirImportService codeSystemFhirImportService;
  private final Optional<FhirFshConverter> fhirFshConverter;

  @Override
  public String getName() {
    return "codesystem";
  }

  @Override
  public Map<String, String> getContent(Long spaceId) {
    List<CodeSystem> codeSystems = codeSystemService.query(new CodeSystemQueryParams().setSpaceId(spaceId).all()).getData();
    Map<String, String> result = new HashMap<>();
    codeSystems.forEach(cs -> {
      codeSystemVersionService.query(new CodeSystemVersionQueryParams().setCodeSystem(cs.getId())).getData().forEach(csv -> {
        List<Provenance> provenances = provenanceService.find("CodeSystemVersion|" + csv.getId());
        csv.setEntities(codeSystemEntityVersionService.query(new CodeSystemEntityVersionQueryParams()
            .setCodeSystemVersionId(csv.getId())
            .all()).getData());
        String json = CodeSystemFhirMapper.toFhirJson(cs, csv, provenances);
        String prettyJson = JsonUtil.toPrettyJson(JsonUtil.toMap(json));
        String fhirId = CodeSystemFhirMapper.toFhirId(cs, csv);
        result.put(fhirId + ".json", prettyJson);
        fhirFshConverter.ifPresent(c -> result.put(fhirId + ".fsh", c.toFsh(json).join()));
      });
    });
    return result;
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    content.forEach((f, c) -> {
      if (!f.endsWith(".json")) {
        return;
      }
      String[] ids = CodeSystemFhirMapper.parseCompositeId(StringUtils.removeEnd(f, ".json"));
      String csId = ids[0];
      String version = ids[1];
      if (c == null) {
        codeSystemVersionService.load(csId, version).ifPresent(csv -> {
          codeSystemVersionService.cancel(csv.getId(), csv.getCodeSystem());
        });
        return;
      }
      codeSystemFhirImportService.importCodeSystem(c, csId);
    });
  }

}
