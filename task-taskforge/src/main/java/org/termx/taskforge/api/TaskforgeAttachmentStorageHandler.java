package org.termx.taskforge.api;

import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;

public interface TaskforgeAttachmentStorageHandler {
  String saveAttachment(String path, Attachment attachment);
}
