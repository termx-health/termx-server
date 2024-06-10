package com.kodality.termx.terminology.terminology.valueset.providers;


import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.terminology.fhir.valueset.ValueSetFhirImportService;
import com.kodality.termx.terminology.fhir.valueset.ValueSetFhirMapper;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataValueSetFhirHandler implements SpaceGithubDataHandler {
  private final ResourceContentValueSetVersionFhirProvider resourceContentProvider;
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetFhirImportService valueSetFhirImportService;

  @Override
  public String getName() {
    return "valueset-fhir-json";
  }

  @Override
  public String getDefaultDir() {
    return "input/vocabulary/value-sets";
  }

  @Override
  public List<ResourceContent> getContent(Long spaceId) {
    List<ValueSet> valueSets = valueSetService.query(new ValueSetQueryParams().setSpaceId(spaceId).all()).getData();
    return valueSets.stream().flatMap(vs -> {
      return valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSet(vs.getId())).getData().stream().flatMap(vsv -> {
        return resourceContentProvider.getContent(vs, vsv).stream();
      });
    }).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    content.forEach((f, c) -> {
      if (!f.endsWith(".json")) {
        return;
      }
      String[] ids = ValueSetFhirMapper.parseCompositeId(StringUtils.removeEnd(f, ".json"));
      String vsId = ids[0];
      String version = ids[1];
      if (c == null) {
        valueSetVersionService.load(vsId, version).ifPresent(vsv -> {
          valueSetVersionService.cancel(vsv.getId());
        });
        return;
      }
      valueSetFhirImportService.importValueSet(c, vsId);
    });
  }

}
