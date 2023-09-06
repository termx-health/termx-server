package com.kodality.termx.wiki.pagecomment;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentQueryParams;
import java.util.Objects;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class PageCommentService {
  private final ProvenanceService provenanceService;
  private final PageCommentRepository commentRepository;

  public QueryResult<PageComment> query(PageCommentQueryParams params) {
    return commentRepository.query(params);
  }

  public PageComment create(PageComment comment) {
    PageComment persisted = internalSave(comment);
    provenanceService.create(new Provenance("created", "PageComment", persisted.getId().toString()));
    return commentRepository.load(persisted.getId());
  }

  public PageComment update(PageComment comment) {
    validateAuthor(comment.getId());
    PageComment updated = internalSave(comment);
    provenanceService.create(new Provenance("modified", "PageComment", updated.getId().toString()));
    return commentRepository.load(updated.getId());
  }

  public void delete(Long id) {
    validateAuthor(id);
    provenanceService.create(new Provenance("deleted", "PageComment", id.toString()));
    commentRepository.delete(id);

    commentRepository.loadReplyIds(id).forEach(replyId -> {
      provenanceService.create(new Provenance("deleted", "PageComment", replyId.toString()));
      commentRepository.delete(replyId);
    });
  }

  private PageComment internalSave(PageComment comment) {
    if (comment.getStatus() == null) {
      comment.setStatus("active");
    }
    Long id = commentRepository.save(comment);
    comment.setId(id);
    return comment;
  }


  public PageComment resolve(Long id) {
    commentRepository.resolve(id);
    provenanceService.create(new Provenance("resolved", "PageComment", id.toString()));
    return commentRepository.load(id);
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
      throw new RuntimeException("Comment can be changed only by its author");
    }
  }
}
