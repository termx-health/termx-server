package org.termx.terminology.terminology.relatedartifacts;

import org.termx.core.sys.space.SpaceService;
import org.termx.core.wiki.PageProvider;
import org.termx.sys.space.Space;
import org.termx.sys.space.SpaceQueryParams;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.mapset.version.MapSetVersionService;
import org.termx.terminology.terminology.valueset.ValueSetService;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.mapset.MapSetVersion;
import org.termx.ts.mapset.MapSetVersionQueryParams;
import org.termx.ts.relatedartifact.RelatedArtifact;
import org.termx.ts.relatedartifact.RelatedArtifactType;
import org.termx.ts.valueset.ValueSet;
import org.termx.ts.valueset.ValueSetQueryParams;
import org.termx.wiki.page.PageContent;
import org.termx.wiki.page.PageRelationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemRelatedArtifactService extends RelatedArtifactService {
  private final CodeSystemService codeSystemService;
  private final ValueSetService valueSetService;
  private final MapSetVersionService mapSetVersionService;
  private final Optional<PageProvider> pageProvider;
  private final SpaceService spaceService;

  @Override
  public String getResourceType() {
    return "CodeSystem";
  }

  @Override
  public List<RelatedArtifact> findRelatedArtifacts(String id) {
    List<RelatedArtifact> artifacts = new ArrayList<>();
    artifacts.addAll(findSupplement(id));
    artifacts.addAll(findValueSets(id));
    artifacts.addAll(findMapSets(id));
    artifacts.addAll(findPages(id));
    artifacts.addAll(findSpaces(id));
    return artifacts;
  }

  private List<RelatedArtifact> findSupplement(String id) {
    Optional<String> supplementCs = codeSystemService.load(id).map(CodeSystem::getBaseCodeSystem);
    List<RelatedArtifact> ra = supplementCs.map(cs -> new RelatedArtifact().setType(RelatedArtifactType.cs).setId(cs)).stream().toList();
    ra.addAll(codeSystemService.query(new CodeSystemQueryParams().setBaseCodeSystem(id).all()).getData().stream()
        .map(cs -> new RelatedArtifact().setType(RelatedArtifactType.cs).setId(cs.getId())).toList());
    return ra;
  }

  private List<RelatedArtifact> findValueSets(String id) {
    List<ValueSet> valueSets = valueSetService.query(new ValueSetQueryParams().setCodeSystem(id).all()).getData();
    return valueSets.stream().map(vs -> new RelatedArtifact().setId(vs.getId()).setType(RelatedArtifactType.vs)).toList();
  }

  private List<RelatedArtifact> findMapSets(String id) {
    List<MapSetVersion> versions = new ArrayList<>();
    versions.addAll(mapSetVersionService.query(new MapSetVersionQueryParams().setScopeSourceCodeSystem(id).all()).getData());
    versions.addAll(mapSetVersionService.query(new MapSetVersionQueryParams().setScopeTargetCodeSystem(id).all()).getData());
    return versions.stream().map(v -> new RelatedArtifact().setId(v.getMapSet()).setType(RelatedArtifactType.ms)).toList();
  }

  private List<RelatedArtifact> findPages(String id) {
    List<PageContent> pages = pageProvider.map(p -> p.getRelatedPageContents(id, PageRelationType.cs)).orElse(List.of());

    String spaceIds = pages.stream().map(PageContent::getSpaceId).distinct().map(String::valueOf).collect(Collectors.joining(","));
    Map<Long, String> spaces = spaceService.query(new SpaceQueryParams().setIds(spaceIds).limit(spaceIds.split(",").length))
        .getData().stream().collect(Collectors.toMap(Space::getId, Space::getCode));

    return pages.stream().map(p -> new RelatedArtifact().setId(spaces.get(p.getSpaceId()) + "|" + p.getSlug()).setType(RelatedArtifactType.p)).toList();
  }

  private List<RelatedArtifact> findSpaces(String id) {
    return spaceService.query(new SpaceQueryParams().setResource("code-system|" + id).all()).getData().stream().map(s ->
        new RelatedArtifact().setId(s.getCode() + "|" + s.getId()).setType(RelatedArtifactType.s)).toList();
  }
}
