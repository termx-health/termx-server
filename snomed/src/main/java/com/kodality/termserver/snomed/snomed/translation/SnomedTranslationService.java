package com.kodality.termserver.snomed.snomed.translation;

import com.kodality.commons.model.CodeName;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskContextItem;
import com.kodality.termserver.snomed.concept.SnomedTranslation;
import com.kodality.termserver.snomed.concept.SnomedTranslationStatus;
import com.kodality.termserver.taskflow.CommonTaskService;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;

@Singleton
@RequiredArgsConstructor
public class SnomedTranslationService {
  private final CommonTaskService taskService;
  private final SnomedTranslationRepository repository;

  public static final String DESCRIPTION_PARTITION_IDENTIFIER = "11";
  public static final String NAMESPACE_IDENTIFIER = "1000265";
  public static final String TASK_CTX_TYPE = "snomed-translation";
  public static final String WORKFLOW = "snomed-translation-validation";

  public List<SnomedTranslation> load(String conceptId) {
    return decorate(repository.load(conceptId));
  }

  public SnomedTranslation load(Long id) {
    return repository.load(id);
  }

  public void save(String conceptId, List<SnomedTranslation> translations) {
    List<Long> cancelledTranslations = repository.retain(conceptId, translations);
    taskService.cancelTasks(cancelledTranslations, TASK_CTX_TYPE);
    if (CollectionUtils.isNotEmpty(translations)) {
      translations.forEach(t -> save(conceptId, t));
    }
  }

  private void save(String conceptId, SnomedTranslation t) {
    prepare(t);
    repository.save(conceptId, t);
    createTask(conceptId, t);
    if (t.getDescriptionId() == null) {
      saveDescription(t);
    }
  }

  public void updateStatus(Long id, String status) {
    repository.updateStatus(id, status);
  }

  private void createTask(String conceptId, SnomedTranslation t) {
    Task task = new Task();
    task.setBusinessStatus(t.getStatus());
    task.setTitle(String.format("%s concept translation validation", conceptId));
    task.setContent(String.format("Module: %s \nLanguage: %s \nTerm: %s \nType: %s \nAcceptability: %s",
        t.getModule(), t.getLanguage(), t.getTerm(), t.getType(), t.getAcceptability()));
    task.setContext(List.of(new TaskContextItem().setId(t.getId()).setType(TASK_CTX_TYPE)));
    taskService.createTask(task, WORKFLOW);
  }

  private void prepare(SnomedTranslation t) {
    t.setStatus(t.getStatus() == null ? SnomedTranslationStatus.proposed : t.getStatus());
  }

  private void saveDescription(SnomedTranslation t) {
    try {
      repository.saveDescriptionId(t.getId(), composeSCTID(t.getId()));
    } catch (CheckDigitException e) {
      e.printStackTrace();
    }
  }

  private String composeSCTID(Long id) throws CheckDigitException {
    VerhoeffCheckDigit verhoeffCheckDigit = new VerhoeffCheckDigit();
    String checkDigit = verhoeffCheckDigit.calculate(id + NAMESPACE_IDENTIFIER + DESCRIPTION_PARTITION_IDENTIFIER);
    return id + NAMESPACE_IDENTIFIER + DESCRIPTION_PARTITION_IDENTIFIER + checkDigit;
  }

  private List<SnomedTranslation> decorate(List<SnomedTranslation> translations) {
    if (CollectionUtils.isEmpty(translations)) {
      return translations;
    }
    String ctx = translations.stream().map(t -> TASK_CTX_TYPE + "|" + t.getId()).collect(Collectors.joining(","));
    Map<String, List<CodeName>> tasks = taskService.findTaskCtxGroup(ctx);
    translations.forEach(t -> {
      List<CodeName> taskRef = tasks.get(TASK_CTX_TYPE + "|" + t.getId());
      if (taskRef != null) {
        t.setTask(taskRef.stream().findFirst().orElse(null));
      }
    });
    return translations;
  }

}
