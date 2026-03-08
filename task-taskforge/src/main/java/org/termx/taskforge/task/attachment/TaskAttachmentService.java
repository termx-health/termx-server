package org.termx.taskforge.task.attachment;

import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import org.termx.taskforge.api.TaskforgeAttachmentStorageHandler;
import org.termx.taskforge.task.Task.TaskAttachment;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskAttachmentService {
  private final TaskAttachmentRepository repository;
  private final Optional<TaskforgeAttachmentStorageHandler> attachmentStorageHandler;

  public List<TaskAttachment> loadAll(Long taskId) {
    return repository.loadAll(taskId);
  }

  public List<TaskAttachment> save(Long taskId, List<TaskAttachment> patientAttachments, Map<String, Attachment> newAttachments) {
    repository.retain(taskId, patientAttachments);
    if (patientAttachments == null) {
      return null;
    }
    patientAttachments.stream().filter(pa -> pa.getAttachmentKey() != null).forEach(pa -> {
      Attachment attachment = newAttachments.get(pa.getAttachmentKey());
      if (attachment == null) {
        throw new RuntimeException("attachment " + pa.getAttachmentKey() + " not found");
      }
      String id = saveAttachment(taskId, attachment);
      pa.setFileId(id);
      pa.setAttachmentKey(null);
      repository.insert(taskId, pa);
    });
    return patientAttachments;
  }

  private String saveAttachment(Long taskId, Attachment attachment) {
    return attachmentStorageHandler.orElseThrow(() -> new RuntimeException("taskforge storage handler not implemented"))
        .saveAttachment("taskforge/" + taskId, attachment);
  }
}
