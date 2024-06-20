package com.kodality.termx.wiki.pageattachment;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.termx.wiki.WikiAttachmentStorageHandler;
import com.kodality.termx.wiki.WikiAttachmentStorageHandler.WikiAttachmentQueryParams;
import com.kodality.termx.wiki.page.Page;
import com.kodality.termx.wiki.page.Page.PageAttachment;
import com.kodality.termx.wiki.page.PageRepository;
import io.micronaut.http.server.types.files.StreamedFile;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class PageAttachmentService {
  private final PageRepository pageService;
  private final Optional<WikiAttachmentStorageHandler> attachmentHandler;


  public Map<String, PageAttachment> saveAttachments(Long id, Map<String, Attachment> attachments) {
    WikiAttachmentStorageHandler storage = getStorage();
    Page page = pageService.load(id);
    if (page == null) {
      throw new NotFoundException("Page not found: " + id);
    }
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

  public PageAttachment getAttachment(Long id, String fileName) {
    WikiAttachmentQueryParams params = new WikiAttachmentQueryParams();
    params.setMeta(Map.of("page", id, "fileName", fileName));
    List<PageAttachment> attachments = getStorage().queryAttachments(params);
    if (attachments.size() != 1) {
      throw new ApiClientException("Attachment does not exist");
    }
    return attachments.get(0);
  }

  public StreamedFile getAttachmentContent(Long id, String fileName) {
    WikiAttachmentStorageHandler storage = getStorage();
    String uuid = getAttachment(id, fileName).getFileId();
    return storage.getAttachmentContent(uuid);
  }

  public void deleteAttachmentContent(Long id, String fileName) {
    WikiAttachmentStorageHandler storage = getStorage();
    String uuid = getAttachment(id, fileName).getFileId();
    storage.deleteAttachment(uuid);
  }

  private WikiAttachmentStorageHandler getStorage() {
    return attachmentHandler.orElseThrow(() -> new RuntimeException("attachments not implemented"));
  }
}
