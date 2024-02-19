package com.kodality.termx.terminology.terminology.codesystem.providers;


import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ResourceContentCodeSystemVersionFhirProvider implements ResourceContentProvider {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ProvenanceService provenanceService;
  private final CodeSystemFhirMapper mapper;

  @Override
  public String getResourceType() {
    return "CodeSystem";
  }

  @Override
  public String getContentType() {
    return "fhir";
  }

  @Override
  public List<ResourceContent> getContent(String idVersionPipe) {
    String[] pipe = PipeUtil.parsePipe(idVersionPipe);
    return getContent(pipe[0], pipe[1]);
  }

  public List<ResourceContent> getContent(String id, String version) {
    CodeSystem cs = codeSystemService.load(id).orElseThrow(() -> new NotFoundException("CodeSystem not found: " + id));
    CodeSystemVersion csv = codeSystemVersionService.load(id, version)
        .orElseThrow(() -> new NotFoundException("CodeSystemVersion not found: " + id + "@" + version));
    return getContent(cs, csv);
  }

  public List<ResourceContent> getContent(CodeSystem cs, CodeSystemVersion csv) {
    List<Provenance> provenances = provenanceService.find("CodeSystemVersion|" + csv.getId());
    csv.setEntities(codeSystemEntityVersionService.query(new CodeSystemEntityVersionQueryParams()
        .setCodeSystemVersionId(csv.getId())
        .all()).getData());
    String json = mapper.toFhirJson(cs, csv, provenances);
    String prettyJson = JsonUtil.toPrettyJson(JsonUtil.toMap(json));
    String fhirId = CodeSystemFhirMapper.toFhirId(cs, csv);
    return List.of(new ResourceContent(fhirId + ".json", prettyJson));
  }

}
