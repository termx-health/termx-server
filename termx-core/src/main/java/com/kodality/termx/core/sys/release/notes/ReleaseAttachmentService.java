package com.kodality.termx.core.sys.release.notes;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.termx.core.sys.release.ReleaseAttachmentStorageHandler;
import com.kodality.termx.core.sys.release.ReleaseAttachmentStorageHandler.ReleaseAttachmentQueryParams;
import com.kodality.termx.core.sys.release.ReleaseRepository;
import com.kodality.termx.sys.release.Release;
import com.kodality.termx.sys.release.ReleaseAttachment;
import io.micronaut.http.server.types.files.StreamedFile;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ReleaseAttachmentService {
  private final ReleaseRepository releaseRepository;
  private final Optional<ReleaseAttachmentStorageHandler> attachmentHandler;
  private final static String KEY = "release";

  public Map<String, ReleaseAttachment> saveAttachments(Long id, Map<String, Attachment> attachments) {
    ReleaseAttachmentStorageHandler storage = getStorage();
    Release release = releaseRepository.load(id);
    if (release == null) {
      throw new NotFoundException("Release not found: " + id);
    }
    return attachments.entrySet().stream().collect(Collectors.toMap(
        Entry::getKey,
        e -> storage.saveAttachment("releases/" + release.getId(), e.getValue(), Map.of(KEY, release.getId(), "fileName", e.getValue().getFileName()))
    ));
  }

  public List<ReleaseAttachment> getAttachments(Long id) {
    ReleaseAttachmentStorageHandler storage = getStorage();
    ReleaseAttachmentQueryParams params = new ReleaseAttachmentQueryParams();
    params.setMeta(Map.of(KEY, id));
    return storage.queryAttachments(params);
  }

  public ReleaseAttachment getAttachment(Long id, String fileName) {
    ReleaseAttachmentQueryParams params = new ReleaseAttachmentQueryParams();
    params.setMeta(Map.of(KEY, id, "fileName", fileName));
    List<ReleaseAttachment> attachments = getStorage().queryAttachments(params);
    if (attachments.size() != 1) {
      throw new ApiClientException("Attachment does not exist");
    }
    return attachments.get(0);
  }

  public StreamedFile getAttachmentContent(Long id, String fileName) {
    ReleaseAttachmentStorageHandler storage = getStorage();
    String uuid = getAttachment(id, fileName).getFileId();
    return storage.getAttachmentContent(uuid);
  }

  private ReleaseAttachmentStorageHandler getStorage() {
    return attachmentHandler.orElseThrow(() -> new RuntimeException("attachments not implemented"));
  }
}
