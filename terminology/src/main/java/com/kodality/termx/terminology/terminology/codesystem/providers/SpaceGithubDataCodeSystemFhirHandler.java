package com.kodality.termx.terminology.terminology.codesystem.providers;


import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
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
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataCodeSystemFhirHandler implements SpaceGithubDataHandler {
  private final ResourceContentCodeSystemVersionFhirProvider resourceContentProvider;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
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
