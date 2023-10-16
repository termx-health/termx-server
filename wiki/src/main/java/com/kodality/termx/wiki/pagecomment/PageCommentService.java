package com.kodality.termx.wiki.pagecomment;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.wiki.ApiError;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentQueryParams;
import com.kodality.termx.wiki.page.PageCommentStatus;
import com.kodality.termx.wiki.pagecomment.diff.PageCommentDiffUtil;
import com.kodality.termx.wiki.pagecomment.interceptors.PageCommentInterceptorService;
import java.util.List;
import java.util.Objects;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class PageCommentService {
  private final ProvenanceService provenanceService;
  private final PageCommentRepository commentRepository;
  private final PageCommentInterceptorService interceptor;

  public PageComment load(Long id) {
    return commentRepository.load(id);
  }

  public QueryResult<PageComment> query(PageCommentQueryParams params) {
    return commentRepository.query(params);
  }

  @Transactional
  public PageComment create(PageComment comment) {
    PageComment persisted = internalSave(comment);
    provenanceService.create(new Provenance("created", "PageComment", persisted.getId().toString()));

    PageComment c = commentRepository.load(persisted.getId());
    interceptor.afterCommentCreate(c);
    return c;
  }

  @Transactional
  public PageComment update(PageComment comment) {
    validateAuthor(comment.getId());
    PageComment updated = internalSave(comment);
    provenanceService.create(new Provenance("modified", "PageComment", updated.getId().toString()));

    PageComment c = commentRepository.load(updated.getId());
    interceptor.afterCommentUpdate(c);
    return c;
  }

  private PageComment internalSave(PageComment comment) {
    if (comment.getStatus() == null) {
      comment.setStatus("active");
    }
    Long id = commentRepository.save(comment);
    comment.setId(id);
    return comment;
  }


  @Transactional
  public void delete(Long id) {
    validateAuthor(id);
    internalDelete(id);
  }

  @Transactional
  public void deleteByPage(Long pageId) {
    query(new PageCommentQueryParams().setPageIds(pageId.toString()).all()).getData().forEach(comment -> {
      internalDelete(comment.getId());
    });
  }

  private void internalDelete(Long id) {
    PageComment c = commentRepository.load(id);
    provenanceService.create(new Provenance("deleted", "PageComment", id.toString()));
    commentRepository.delete(id);

    commentRepository.loadReplyIds(id).forEach(replyId -> {
      provenanceService.create(new Provenance("deleted", "PageComment", replyId.toString()));
      commentRepository.delete(replyId);
    });

    interceptor.afterCommentDelete(c);
  }


  @Transactional
  public PageComment resolve(Long id) {
    commentRepository.updateStatus(id, PageCommentStatus.resolved);
    provenanceService.create(new Provenance("resolved", "PageComment", id.toString()));

    commentRepository.loadReplyIds(id).forEach(replyId -> {
      commentRepository.updateStatus(replyId, PageCommentStatus.resolved);
      provenanceService.create(new Provenance("resolved", "PageComment", replyId.toString()));
    });

    PageComment c = commentRepository.load(id);
    interceptor.afterStatusChange(c);
    return c;
  }

  @Transactional
  public void recalculateLineNumbers(List<PageComment> comments, String contentBefore, String contentAfter) {
    comments.stream().filter(com -> com.getOptions() != null).forEach(com -> {
      Integer ln = PageCommentDiffUtil.recalculateLineNumber(com.getOptions().getLineNumber(), contentBefore, contentAfter);
      commentRepository.updateLineNumber(com.getId(), ln);
    });
  }


  private void validateAuthor(Long id) {
    Objects.requireNonNull(id);
    String userName = SessionStore.require().getUsername();

    PageComment comment = commentRepository.load(id);
    boolean isAuthor = comment.getCreatedBy().equals(userName);
    if (!isAuthor && comment.getParentId() != null) {
      isAuthor = commentRepository.load(comment.getParentId()).getCreatedBy().equals(userName);
    }

    if (!isAuthor) {
      throw ApiError.T021.toApiException();
    }
  }
}
