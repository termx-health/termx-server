package com.kodality.termserver.thesaurus.pagerelation;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.common.utils.MatcherUtil;
import com.kodality.termserver.thesaurus.page.PageContent;
import com.kodality.termserver.thesaurus.page.PageRelation;
import com.kodality.termserver.thesaurus.page.PageRelationQueryParams;
import com.kodality.termserver.thesaurus.pagecontent.PageContentRepository;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageRelationService {
  private final PageRelationRepository repository;
  private final PageContentRepository contentRepository;

  public List<PageRelation> loadAll(Long pageId) {
    return repository.loadAll(pageId);
  }

  public QueryResult<PageRelation> query(PageRelationQueryParams params) {
    QueryResult<PageRelation> query = repository.query(params);
    query.getData().forEach(this::decorate);
    return query;
  }

  @Transactional
  public void save(PageContent content, Long pageId) {
    List<PageRelation> relations = merge(extractFromContent(content), loadAll(pageId));
    repository.retain(relations, pageId, content.getId());
    if (CollectionUtils.isNotEmpty(relations)) {
      relations.forEach(r -> repository.save(r, pageId, content.getId()));
    }
  }

  public List<PageRelation> extractFromContent(PageContent content) {
    List<PageRelation> relations = new ArrayList<>();
    List<String> matches = MatcherUtil.findAllMatches(content.getContent(), "\\[(.*?)\\]\\(.*?\\)");
    matches.forEach(m -> {
      String target = m.split("]")[1].split("\\(|\\)")[1];
      if (!target.contains(":")) {
        return;
      }
      String type = target.split(":")[0];
      if (!List.of("cs", "vs", "ms", "concept", "page").contains(type)) {
        return;
      }
      target = target.split(":")[1];

      PageRelation relation = new PageRelation();
      relation.setType(type);
      relation.setContent(new CodeName(content.getId()));
      relation.setTarget(target);
      relations.add(relation);
    });
    return relations;
  }

  private List<PageRelation> merge(List<PageRelation> extractedRelations, List<PageRelation> currentRelations) {
    extractedRelations.forEach(extracted -> extracted.setId(currentRelations.stream().filter(current ->
                current.getType().equals(extracted.getType()) &&
                current.getTarget().equals(extracted.getTarget()) &&
                current.getContent().getId().equals(extracted.getContent().getId()))
        .findFirst().map(PageRelation::getId).orElse(null)));
    return extractedRelations;
  }

  private PageRelation decorate(PageRelation relation) {
    PageContent content = contentRepository.load(relation.getContent().getId());
    relation.getContent().setCode(content.getSlug()).setNames(new LocalizedName(Map.of(content.getLang(), content.getName())));
    return relation;
  }
}
