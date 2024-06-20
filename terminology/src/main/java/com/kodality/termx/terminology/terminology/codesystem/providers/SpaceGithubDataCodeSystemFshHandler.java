package com.kodality.termx.terminology.terminology.codesystem.providers;


import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataCodeSystemFshHandler implements SpaceGithubDataHandler {
  private final ResourceContentCodeSystemVersionFshProvider resourceContentProvider;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;

  @Override
  public String getName() {
    return "codesystem-fhir-fsh";
  }

  @Override
  public String getDefaultDir() {
    return "input/fsh/code-systems";
  }

  @Override
  public List<ResourceContent> getContent(Long spaceId) {
    List<CodeSystem> codeSystems = codeSystemService.query(new CodeSystemQueryParams().setSpaceId(spaceId).all()).getData();
    return codeSystems.stream().flatMap(cs -> {
      return codeSystemVersionService.query(new CodeSystemVersionQueryParams().setCodeSystem(cs.getId())).getData().stream().flatMap(csv -> {
        return resourceContentProvider.getContent(cs, csv).stream();
      });
    }).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    // do nothing. save using fhir json
  }

}
