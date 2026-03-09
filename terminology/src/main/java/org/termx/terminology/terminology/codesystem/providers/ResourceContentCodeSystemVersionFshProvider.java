package org.termx.terminology.terminology.codesystem.providers;


import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.PipeUtil;
import org.termx.core.github.ResourceContentProvider;
import org.termx.core.sys.provenance.Provenance;
import org.termx.core.sys.provenance.ProvenanceService;
import org.termx.terminology.fhir.FhirFshConverter;
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemVersion;
import io.micronaut.context.annotation.Requires;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
@Requires(bean = FhirFshConverter.class)
public class ResourceContentCodeSystemVersionFshProvider implements ResourceContentProvider {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ProvenanceService provenanceService;
  private final FhirFshConverter fhirFshConverter;
  private final CodeSystemFhirMapper mapper;

  @Override
  public String getResourceType() {
    return "CodeSystem";
  }

  @Override
  public String getContentType() {
    return "fsh";
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
    String fhirId = CodeSystemFhirMapper.toFhirId(cs, csv);
    return List.of(new ResourceContent(fhirId + ".fsh", fhirFshConverter.toFsh(json).join()));
  }

}
