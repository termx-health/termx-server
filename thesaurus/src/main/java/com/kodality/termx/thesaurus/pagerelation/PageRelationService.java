package com.kodality.termx.thesaurus.pagerelation;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.thesaurus.page.PageContent;
import com.kodality.termx.thesaurus.page.PageRelation;
import com.kodality.termx.thesaurus.page.PageRelationQueryParams;
import com.kodality.termx.thesaurus.pagecontent.PageContentRepository;
import com.kodality.termx.utils.MatcherUtil;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageRelationService {
  private final PageRelationRepository repository;
  private final PageContentRepository contentRepository;

  public List<PageRelation> loadAll(Long pageId) {
    return repository.loadAll(pageId).stream().map(this::decorate).toList();
  }

  public QueryResult<PageRelation> query(PageRelationQueryParams params) {
    return repository.query(params).map(this::decorate);
  }

  @Transactional
  public void save(PageContent content, Long pageId) {
    List<PageRelation> relations = merge(parseRelations(content), loadAll(pageId));
    repository.retain(relations, pageId, content.getId());
    if (CollectionUtils.isNotEmpty(relations)) {
      relations.forEach(r -> repository.save(r, pageId, content.getId()));
    }
  }


  private List<PageRelation> parseRelations(PageContent content) {
    List<PageRelation> relations = new ArrayList<>();
    relations.addAll(parseRelationLinks(content));
    relations.addAll(parseRelationInsertions(content));
    return relations;
  }

  private List<PageRelation> parseRelationLinks(PageContent content) {
    String regex = "\\[(.*?)]\\((.*?)\\)"; // [label](uri)
    List<String> allowedTypes = List.of("cs", "vs", "ms", "concept", "page");

    return MatcherUtil.findAllMatches(content.getContent(), regex).stream().map(m -> {
      Matcher matcher = Pattern.compile(regex).matcher(m);
      if (!matcher.find()){
        return null;
      }
      String uri = matcher.group(2);
      if (!uri.contains(":")) {
        return null;
      }
      String type = uri.split(":")[0];
      String target = uri.split(":")[1];
      if (!allowedTypes.contains(type)) {
        return null;
      }
      return new PageRelation()
          .setContent(new CodeName(content.getId()))
          .setType(type)
          .setTarget(target);
    }).filter(Objects::nonNull).toList();
  }

  private List<PageRelation> parseRelationInsertions(PageContent content) {
    String regex = "\\{\\{(.*):(.*)}}"; // {{system:value}}
    List<String> allowedSystems = List.of("def");

    return MatcherUtil.findAllMatches(content.getContent(), regex).stream().map(m -> {
      Matcher matcher = Pattern.compile(regex).matcher(m);
      if (!matcher.find()){
        return null;
      }
      String system = matcher.group(1);
      String value = matcher.group(2);
      if (!allowedSystems.contains(system)) {
        return null;
      }
      return new PageRelation()
          .setContent(new CodeName(content.getId()))
          .setType(system)
          .setTarget(value);
    }).filter(Objects::nonNull).toList();
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
    relation.getContent()
        .setCode(content.getSlug())
        .setNames(new LocalizedName(Map.of(content.getLang(), content.getName())));
    return relation;
  }
}
