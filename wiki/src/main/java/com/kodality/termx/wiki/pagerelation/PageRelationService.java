package com.kodality.termx.wiki.pagerelation;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.utils.MatcherUtil;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageRelation;
import com.kodality.termx.wiki.page.PageRelationQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageRelationService {
  private final PageRelationRepository repository;

  public List<PageRelation> loadAll(Long pageId) {
    return repository.loadAll(pageId);
  }

  public QueryResult<PageRelation> query(PageRelationQueryParams params) {
    return repository.query(params);
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
      if (!matcher.find()) {
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
      if (!matcher.find()) {
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
    return extractedRelations.stream().peek(ext -> {
      Predicate<PageRelation> isSameRelation = p -> {
        String a = p.getType() + "#" + p.getTarget() + "#" + p.getContent().getId();
        String b = ext.getType() + "#" + ext.getTarget() + "#" + ext.getContent().getId();
        return Objects.equals(a, b);
      };

      Long persistedId = currentRelations.stream()
          .filter(isSameRelation)
          .findFirst()
          .map(PageRelation::getId)
          .orElse(null);

      ext.setId(persistedId);
    }).toList();
  }
}
