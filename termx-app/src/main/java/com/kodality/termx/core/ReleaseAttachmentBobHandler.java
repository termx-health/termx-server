package com.kodality.termx.core;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.bob.BobObject;
import com.kodality.termx.bob.BobObjectQueryParams;
import com.kodality.termx.bob.BobObjectService;
import com.kodality.termx.bob.BobStorage;
import com.kodality.termx.core.sys.release.ReleaseAttachmentStorageHandler;
import com.kodality.termx.sys.release.ReleaseAttachment;
import io.micronaut.http.server.types.files.StreamedFile;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ReleaseAttachmentBobHandler implements ReleaseAttachmentStorageHandler {
  private final BobObjectService objectService;
  private final static String CONTAINER = "release";

  @Override
  public List<ReleaseAttachment> queryAttachments(ReleaseAttachmentQueryParams params) {
    BobObjectQueryParams bobParams = new BobObjectQueryParams();
    if (params.getMeta() != null && !params.getMeta().isEmpty()) {
      bobParams.setMeta(JsonUtil.toJson(params.getMeta())).all();
    }
    return objectService.query(bobParams).getData().stream().map(this::mapToAttachment).toList();
  }

  @Override
  public ReleaseAttachment saveAttachment(String path, Attachment a, Map<String, Object> meta) {
    checkDuplicate(a, meta);
    BobObject o = new BobObject();
    o.setContentType(a.getContentType());
    o.setMeta(meta);
    o.setStorage(new BobStorage()
        .setContainer(CONTAINER)
        .setPath(path)
        .setFilename(a.getFileName())
    );
    String uuid = objectService.store(o, a.getContent());
    BobObject object = objectService.load(uuid);
    return mapToAttachment(object);
  }

  @Override
  public StreamedFile getAttachmentContent(String uuid) {
    BobObject object = objectService.load(uuid);
    if (object == null) {
      throw new NotFoundException("File '" + uuid + "' does not exist.");
    }
    return objectService.loadContent(object);
  }

  @Override
  public void deleteAttachment(String uuid) {
    objectService.delete(uuid);
  }

  private void checkDuplicate(Attachment a, Map<String, Object> meta) {
    List<ReleaseAttachment> existingFiles = queryAttachments(new ReleaseAttachmentQueryParams().setMeta(meta));
    Optional<ReleaseAttachment> existingFile = existingFiles.stream().filter(f -> f.getFileName().equals(a.getFileName())).findFirst();
    existingFile.ifPresent(releaseAttachment -> deleteAttachment(releaseAttachment.getFileId()));
  }

  private ReleaseAttachment mapToAttachment(BobObject o) {
    return new ReleaseAttachment()
        .setFileId(o.getUuid())
        .setFileName(o.getStorage().getFilename())
        .setContentType(o.getContentType());
  }
}
