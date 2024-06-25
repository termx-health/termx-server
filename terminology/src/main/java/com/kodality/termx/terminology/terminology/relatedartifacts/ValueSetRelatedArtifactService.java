package com.kodality.termx.terminology.terminology.relatedartifacts;

import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.SpaceQueryParams;
import com.kodality.termx.core.sys.space.SpaceService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.relatedartifact.RelatedArtifact;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.termx.core.wiki.PageProvider;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageRelationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetRelatedArtifactService extends RelatedArtifactService {
  private final ValueSetVersionService valueSetVersionService;
  private final PageProvider pageProvider;
  private final SpaceService spaceService;

  @Override
  public String getResourceType() {
    return "ValueSet";
  }

  @Override
  public List<RelatedArtifact> findRelatedArtifacts(String id) {
    List<RelatedArtifact> artifacts = new ArrayList<>();
    artifacts.addAll(collectFromRules(id));
    artifacts.addAll(findPages(id));
    artifacts.addAll(findSpaces(id));
    return artifacts;
  }

  private List<RelatedArtifact> collectFromRules(String id) {
    List<ValueSetVersion> versions = valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSet(id).all()).getData();
    return versions.stream().map(ValueSetVersion::getRuleSet).filter(Objects::nonNull).filter(r -> r.getRules() != null)
        .flatMap(r -> r.getRules().stream()).filter(Objects::nonNull).map(r -> {
          if (r.getCodeSystem() != null) {
            return new RelatedArtifact().setId(r.getCodeSystem()).setType("CodeSystem");
          }
          if (r.getValueSet() != null) {
            return new RelatedArtifact().setId(r.getValueSet()).setType("ValueSet");
          }
          return null;
        }).filter(Objects::nonNull).filter(distinctByKey(ra -> ra.getType() + ra.getId())).collect(Collectors.toList());
  }


  private List<RelatedArtifact> findPages(String id) {
    List<PageContent> pages = pageProvider.getRelatedPageContents(id, PageRelationType.vs);

    String spaceIds = pages.stream().map(PageContent::getSpaceId).distinct().map(String::valueOf).collect(Collectors.joining(","));
    Map<Long, String> spaces = spaceService.query(new SpaceQueryParams().setIds(spaceIds).limit(spaceIds.split(",").length))
        .getData().stream().collect(Collectors.toMap(Space::getId, Space::getCode));

    return pages.stream().map(p -> new RelatedArtifact().setId(spaces.get(p.getSpaceId()) + "|" + p.getSlug()).setType("Page")).collect(Collectors.toList());
  }

  private List<RelatedArtifact> findSpaces(String id) {
    return spaceService.query(new SpaceQueryParams().setResource("value-set|" + id).all()).getData().stream().map(s ->
        new RelatedArtifact().setId(s.getCode() + "|" + s.getId()).setType("Space")).toList();
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}
