package com.kodality.termx.terminology.terminology.codesystem.validator;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Singleton
@RequiredArgsConstructor
public class CodeSystemUniquenessValidatorService {
  private final LorqueProcessService lorqueProcessService;
  private final ConceptService conceptService;

  private final static String process = "cs-uniqueness-validation";

  public LorqueProcess validate(CodeSystemUniquenessValidatorRequest request) {
    LorqueProcess lorqueProcess = lorqueProcessService.start(new LorqueProcess().setProcessName(process));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ProcessResult result = ProcessResult.text(composeValidationResult(request));
        lorqueProcessService.complete(lorqueProcess.getId(), result);
      } catch (Exception e) {
        ProcessResult result = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(lorqueProcess.getId(), result);
      }
    }));
    return lorqueProcess;
  }

  private String composeValidationResult(CodeSystemUniquenessValidatorRequest request) {

    ConceptQueryParams params = new ConceptQueryParams().all();
    params.setCodeSystem(request.getCodeSystem());
    params.setCodeSystemVersionId(request.getVersionId());
    List<Concept> concepts = conceptService.query(params).getData();

    Map<String, List<Concept>> groupedConcepts = concepts.stream().collect(Collectors.groupingBy(c -> getGroupingKey(c, request)));

    Map<String, List<Concept>> duplicates =
        groupedConcepts.entrySet().stream().filter(es -> es.getValue().size() > 1 && (!request.isIgnoreEmptyProperties() || !es.getKey().equals("")))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    CodeSystemUniquenessValidatorResult result = new CodeSystemUniquenessValidatorResult();
    result.setDuplicates(duplicates);
    return JsonUtil.toJson(result);
  }

  private String getGroupingKey(Concept c, CodeSystemUniquenessValidatorRequest request) {
    String key = "";
    if (request.isDesignations()) {
      key += c.getVersions().stream()
          .flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(new ArrayList<>()).stream())
          .sorted(Comparator.comparing(Designation::getLanguage))
          .sorted(Comparator.comparing(Designation::getDesignationType))
          .map(d -> d.getDesignationType() + "|" + d.getLanguage() + "|" + d.getName()).collect(Collectors.joining(","));
    }
    if (request.isProperties()) {
      key += c.getVersions().stream()
          .flatMap(v -> Optional.ofNullable(v.getPropertyValues()).orElse(new ArrayList<>()).stream())
          .sorted(Comparator.comparing(EntityPropertyValue::getEntityProperty))
          .map(p -> p.getEntityProperty() + "|" + JsonUtil.toJson(p.getValue())).collect(Collectors.joining(","));
    }
    return key;
  }
}
