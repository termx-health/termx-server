package com.kodality.termx.wiki.pagelink;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.wiki.page.PageLink;
import com.kodality.termx.wiki.page.PageLinkMoveRequest;
import com.kodality.termx.wiki.page.PageLinkQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class PageLinkService {
  private final PageLinkRepository repository;

  public List<Long> getPath(Long targetId) {
    Optional<String> path = repository.getPath(targetId).stream().findFirst();
    return path.isEmpty() ? List.of() : Arrays.stream(path.get().split("\\.")).filter(StringUtils::isNotEmpty).map(Long::valueOf).collect(Collectors.toList());
  }

  public QueryResult<PageLink> query(PageLinkQueryParams params) {
    return repository.query(params);
  }

  public List<PageLink> loadSources(Long targetId) {
    return repository.loadSources(targetId);
  }


  @Transactional
  public void saveRoots(List<PageLink> links) {
    links.forEach(l -> l.setOrderNumber(links.indexOf(l)));

    repository.retainRoots(links);
    if (CollectionUtils.isNotEmpty(links)) {
      links.forEach(repository::save);
    }
    repository.refreshClosureView();
  }

  @Transactional
  public void saveTargets(Long sourceId, List<PageLink> targetLinks) {
    targetLinks.forEach(l -> {
      l.setSourceId(sourceId);
      l.setOrderNumber(targetLinks.indexOf(l));
    });

    repository.retainBySourceId(sourceId, targetLinks);
    if (CollectionUtils.isNotEmpty(targetLinks)) {
      targetLinks.forEach(repository::save);
    }
    repository.refreshClosureView();
  }

  @Transactional
  public void saveSources(List<PageLink> sourceLinks, Long targetId) {
    // NB: order number DOES NOT get set automatically
    sourceLinks.forEach(l -> l.setTargetId(targetId));

    List<Long> roots = repository.loadRoots().stream().map(PageLink::getTargetId).toList();
    Optional<PageLink> rootLink = sourceLinks.stream().filter(rl -> roots.contains(rl.getTargetId())).findFirst();
    if (rootLink.isPresent()) {
      rootLink.get().setId(null);
    } else if (sourceLinks.isEmpty()) {
      sourceLinks.add(new PageLink().setSourceId(targetId).setTargetId(targetId).setOrderNumber(0));
    }

    repository.retainByTargetId(sourceLinks, targetId);
    sourceLinks.forEach(repository::save);

    repository.refreshClosureView();
  }


  @Transactional
  public List<PageLink> moveLink(Long linkId, PageLinkMoveRequest req) {
    if (req.getSiblingLinkId() != null) {
      if ("before".equals(req.getAction())) {
        return moveBefore(linkId, req.getSiblingLinkId());
      }
      if ("after".equals(req.getAction())) {
        return moveAfter(linkId, req.getSiblingLinkId());
      }
    } else if (req.getParentLinkId() != null) {
      return moveToParent(linkId, req.getParentLinkId());
    }

    throw new RuntimeException("couldn't match the page link move request's arguments");
  }

  public List<PageLink> moveToParent(Long linkId, Long parentLinkId) {
    PageLink link = repository.load(linkId);
    repository.close(linkId);

    PageLink parentLink = repository.load(parentLinkId);
    Long parentPageId = parentLink.getTargetId();

    // todo: cycle detection

    List<PageLink> children = repository.loadTargets(parentPageId);
    children.add(new PageLink().setSourceId(parentPageId).setTargetId(link.getTargetId()));
    saveTargets(parentPageId, children);

    return children;
  }

  public List<PageLink> moveBefore(Long pageId, Long siblingPageId) {
    return moveSibling(pageId, siblingPageId, false);
  }

  public List<PageLink> moveAfter(Long pageId, Long siblingPageId) {
    return moveSibling(pageId, siblingPageId, true);
  }

  public List<PageLink> moveSibling(Long linkId, Long siblingLinkId, boolean after) {
    PageLink link = repository.load(linkId);
    PageLink siblingLink = repository.load(siblingLinkId);

    repository.close(link.getId());

    Long parentId;
    List<PageLink> children;
    if (isRoot(siblingLink)) {
      // if sibling is the root link, then the new link should become root (source = target) as well
      parentId = link.getTargetId();
      children = repository.loadRoots();
    } else {
      parentId = siblingLink.getSourceId();
      children = repository.loadTargets(parentId);
    }

    // todo: cycle detection
    if (children.stream().anyMatch(c -> c.getTargetId().equals(link.getTargetId()))) {
      throw new RuntimeException("link already exists");
    }

    int siblingIndex = children.stream()
        .filter(l -> l.getTargetId().equals(siblingLink.getTargetId())).findFirst()
        .map(children::indexOf)
        .orElse(0);

    PageLink newLink = new PageLink().setSourceId(parentId).setTargetId(link.getTargetId());
    if (after) {
      children.add(siblingIndex + 1, newLink);
    } else {
      children.add(Integer.max(0, siblingIndex), newLink);
    }

    if (isRoot(siblingLink)) {
      saveRoots(children);
    } else {
      saveTargets(parentId, children);
    }
    return children;
  }

  private boolean isRoot(PageLink link) {
    return Objects.equals(link.getSourceId(), link.getTargetId());
  }
}
