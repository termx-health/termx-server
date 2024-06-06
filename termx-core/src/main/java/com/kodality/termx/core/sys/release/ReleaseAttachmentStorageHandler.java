package com.kodality.termx.core.sys.release;

import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.termx.sys.release.ReleaseAttachment;
import io.micronaut.http.server.types.files.StreamedFile;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

public interface ReleaseAttachmentStorageHandler {
  List<ReleaseAttachment> queryAttachments(ReleaseAttachmentQueryParams params);

  ReleaseAttachment saveAttachment(String path, Attachment attachment, Map<String, Object> meta);

  StreamedFile getAttachmentContent(String uuid);

  void deleteAttachment(String uuid);

  @Getter
  @Setter
  @Accessors(chain = true)
  class ReleaseAttachmentQueryParams {
    private Map<String, Object> meta;
  }
}
