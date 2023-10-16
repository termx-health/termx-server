package com.kodality.termx.terminology.terminology.codesystem;


import com.kodality.termx.terminology.fhir.FhirFshConverter;
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
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataCodeSystemFshHandler implements SpaceGithubDataHandler {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ProvenanceService provenanceService;
  private final Optional<FhirFshConverter> fhirFshConverter;

  @Override
  public String getName() {
    return "codesystem-fhir-fsh";
  }

  @Override
  public String getDefaultDir() {
    return "input/fsh/code-systems";
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
        String fhirId = CodeSystemFhirMapper.toFhirId(cs, csv);
        fhirFshConverter.ifPresent(c -> result.put(fhirId + ".fsh", c.toFsh(json).join()));
      });
    });
    return result;
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    // do nothing. save using fhir json
  }

}
