package com.kodality.termx.terminology.valueset;


import com.kodality.termx.fhir.FhirFshConverter;
import com.kodality.termx.fhir.valueset.ValueSetFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
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
public class SpaceGithubDataValueSetFshHandler implements SpaceGithubDataHandler {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ProvenanceService provenanceService;
  private final Optional<FhirFshConverter> fhirFshConverter;

  @Override
  public String getName() {
    return "valueset-fhir-fsh";
  }

  @Override
  public String getDefaultDir() {
    return "vocabulary/valueset-fsh";
  }

  @Override
  public Map<String, String> getContent(Long spaceId) {
    List<ValueSet> valueSets = valueSetService.query(new ValueSetQueryParams().setSpaceId(spaceId).all()).getData();
    Map<String, String> result = new LinkedHashMap<>();
    valueSets.forEach(vs -> {
      valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSet(vs.getId())).getData().forEach(vsv -> {
        List<Provenance> provenances = provenanceService.find("ValueSetVersion|" + vsv.getId());
        String json = ValueSetFhirMapper.toFhirJson(vs, vsv, provenances);
        String fhirId = ValueSetFhirMapper.toFhirId(vs, vsv);
        fhirFshConverter.ifPresent(c -> result.put(fhirId + ".fsh", c.toFsh(json).join()));
      });
    });
    return result;
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    // use json
  }

}
