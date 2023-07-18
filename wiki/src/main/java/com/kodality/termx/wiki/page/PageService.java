package com.kodality.termx.wiki.page;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.wiki.WikiAttachmentStorageHandler;
import com.kodality.termx.wiki.WikiAttachmentStorageHandler.WikiAttachmentQueryParams;
import com.kodality.termx.wiki.page.Page.PageAttachment;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import com.kodality.termx.wiki.pagelink.PageLinkService;
import com.kodality.termx.wiki.pagerelation.PageRelationService;
import com.kodality.termx.wiki.pagetag.PageTagService;
import io.micronaut.http.server.types.files.StreamedFile;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageService {
  private final PageRepository repository;
  private final PageTagService pageTagService;
  private final PageLinkService pageLinkService;
  private final PageContentService pageContentService;
  private final PageRelationService pageRelationService;
  private final Optional<WikiAttachmentStorageHandler> attachmentHandler;

  public Optional<Page> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(this::decorate);
  }

  public QueryResult<Page> query(PageQueryParams params) {
    QueryResult<Page> pages = repository.query(params);
    pages.getData().forEach(this::decorate);
    return pages;
  }

  @Transactional
  public Page save(Page page, PageContent content) {
    repository.save(page);
    pageContentService.save(content, page.getId());
    pageLinkService.saveSources(page.getLinks(), page.getId());
    pageTagService.save(page.getTags(), page.getId());
    return load(page.getId()).orElse(null);
  }

  @Transactional
  public void cancel(Long id) {
    repository.cancel(id);
  }

  private Page decorate(Page page) {
    page.setContents(pageContentService.loadAll(page.getId()));
    page.setLinks(pageLinkService.loadSources(page.getId()));
    page.setTags(pageTagService.loadAll(page.getId()));
    page.setRelations(pageRelationService.loadAll(page.getId()));
    return page;
  }


  public Map<String, PageAttachment> saveAttachments(Long id, Map<String, Attachment> attachments) {
    WikiAttachmentStorageHandler storage = getStorage();
    Page page = load(id).orElseThrow();
    return attachments.entrySet().stream().collect(Collectors.toMap(
        Entry::getKey,
        e -> storage.saveAttachment("pages/" + page.getId(), e.getValue(), Map.of("page", page.getId(), "fileName", e.getValue().getFileName()))
    ));
  }

  public List<PageAttachment> getAttachments(Long id) {
    WikiAttachmentStorageHandler storage = getStorage();
    WikiAttachmentQueryParams params = new WikiAttachmentQueryParams();
    params.setMeta(Map.of("page", id));
    return storage.queryAttachments(params);
  }

  public StreamedFile getAttachmentContent(Long id, String fileName) {
    WikiAttachmentStorageHandler storage = getStorage();

    WikiAttachmentQueryParams params = new WikiAttachmentQueryParams();
    params.setMeta(Map.of("page", id, "fileName", fileName));
    List<PageAttachment> attachments = storage.queryAttachments(params);
    if (attachments.size() != 1) {
      throw new ApiClientException("File does not exist");
    }

    String uuid = attachments.get(0).getFileId();
    return storage.getAttachmentContent(uuid);
  }

  private WikiAttachmentStorageHandler getStorage() {
    return attachmentHandler.orElseThrow(() -> new RuntimeException("attachments not implemented"));
  }
}
