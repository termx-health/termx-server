package com.kodality.termx.terminology.terminology.codesystem;


import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataCodeSystemFhirHandler implements SpaceGithubDataHandler {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ProvenanceService provenanceService;
  private final CodeSystemFhirImportService codeSystemFhirImportService;

  @Override
  public String getName() {
    return "codesystem-fhir-json";
  }

  @Override
  public String getDefaultDir() {
    return "input/vocabulary/code-systems";
  }

  @Override
  public Map<String, String> getContent(Long spaceId) {
    List<CodeSystem> codeSystems = codeSystemService.query(new CodeSystemQueryParams().setSpaceId(spaceId).all()).getData();
    Map<String, String> result = new LinkedHashMap<>();
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