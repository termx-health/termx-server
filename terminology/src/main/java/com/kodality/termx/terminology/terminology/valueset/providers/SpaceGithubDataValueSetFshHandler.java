package com.kodality.termx.terminology.terminology.valueset.providers;


import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataValueSetFshHandler implements SpaceGithubDataHandler {
  private final ResourceContentValueSetVersionFshProvider resourceContentProvider;
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;

  @Override
  public String getName() {
    return "valueset-fhir-fsh";
  }

  @Override
  public String getDefaultDir() {
    return "input/fsh/value-sets";
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
    // use json
  }

}
