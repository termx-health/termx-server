package com.kodality.termx.terminology.terminology.valueset.providers;


import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.terminology.fhir.FhirFshConverter;
import com.kodality.termx.terminology.fhir.valueset.ValueSetFhirMapper;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import io.micronaut.context.annotation.Requires;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
@Requires(bean = FhirFshConverter.class)
public class ResourceContentValueSetVersionFshProvider implements ResourceContentProvider {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ProvenanceService provenanceService;
  private final FhirFshConverter fhirFshConverter;
  private final ValueSetFhirMapper mapper;

  @Override
  public String getResourceType() {
    return "ValueSet";
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
    ValueSet vs = valueSetService.load(id);
    ValueSetVersion vsv = valueSetVersionService.load(id, version)
        .orElseThrow(() -> new NotFoundException("ValueSetVersion not found: " + id + "@" + version));
    return getContent(vs, vsv);
  }

  public List<ResourceContent> getContent(ValueSet vs, ValueSetVersion vsv) {
    List<Provenance> provenances = provenanceService.find("ValueSetVersion|" + vsv.getId());
    String json = mapper.toFhirJson(vs, vsv, provenances);
    String fhirId = ValueSetFhirMapper.toFhirId(vs, vsv);
    return List.of(new ResourceContent(fhirId + ".fsh", fhirFshConverter.toFsh(json).join()));
  }


}
