package com.kodality.termx.wiki;

import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.termx.wiki.page.Page.PageAttachment;
import io.micronaut.http.server.types.files.StreamedFile;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

public interface WikiAttachmentStorageHandler {
  List<PageAttachment> queryAttachments(WikiAttachmentQueryParams params);

  PageAttachment saveAttachment(String path, Attachment attachment, Map<String, Object> meta);

  StreamedFile getAttachmentContent(String uuid);

  void deleteAttachment(String uuid);

  @Getter
  @Setter
  @Accessors(chain = true)
  class WikiAttachmentQueryParams {
    private Map<String, Object> meta;
  }
}
