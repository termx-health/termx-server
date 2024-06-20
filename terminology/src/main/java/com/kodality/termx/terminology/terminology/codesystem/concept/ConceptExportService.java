package com.kodality.termx.terminology.terminology.codesystem.concept;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.core.utils.CsvUtil;
import com.kodality.termx.core.utils.XlsxUtil;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.property.PropertyReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Singleton
@RequiredArgsConstructor
public class ConceptExportService {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final LorqueProcessService lorqueProcessService;

  private final static String process = "cs-concept-export";

  public LorqueProcess export(String codeSystem, String version, String format) {
    LorqueProcess lorqueProcess = lorqueProcessService.start(new LorqueProcess().setProcessName(process));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ProcessResult result = ProcessResult.binary(composeResult(codeSystem, version, format));
        lorqueProcessService.complete(lorqueProcess.getId(), result);
      } catch (Exception e) {
        ProcessResult result = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(lorqueProcess.getId(), result);
      }
    }));

    return lorqueProcess;
  }

  private byte[] composeResult(String codeSystemId, String version, String format) {
    CodeSystem codeSystem = codeSystemService.load(codeSystemId).orElseThrow();
    List<Concept> concepts = conceptService.query(new ConceptQueryParams().setCodeSystem(codeSystemId).setCodeSystemVersion(version).all()).getData();

    List<Pair<String, String>> associations = concepts.stream().flatMap(c -> c.getVersions().stream())
        .flatMap(v -> Optional.ofNullable(v.getAssociations()).orElse(List.of()).stream()
            .filter(a -> PublicationStatus.active.equals(a.getStatus()))
            .map(a -> Pair.of(v.getCode(), a.getTargetCode()))).toList();
    Map<String, List<String>> children = associations.stream().collect(Collectors.groupingBy(Pair::getValue, mapping(Pair::getKey, toList())));
    Map<String, List<String>> parents = associations.stream().collect(Collectors.groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));

    List<String> headers = composeHeaders(codeSystem, concepts);
    List<Object[]> rows = concepts.stream().map(c -> composeRow(c, headers, children, parents)).toList();

    if ("csv".equals(format)) {
      return CsvUtil.composeCsv(headers, rows, ",").toString().getBytes();
    }
    if ("xlsx".equals(format)) {
      return XlsxUtil.composeXlsx(headers, rows, "concepts");
    }
    throw ApiError.TE807.toApiException();
  }

  private List<String> composeHeaders(CodeSystem codeSystem, List<Concept> concepts) {
    List<String> fields = new ArrayList<>();
    fields.add("code");
    fields.addAll(concepts.stream().flatMap(c -> c.getVersions().stream())
        .flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream().filter(d -> PublicationStatus.active.equals(d.getStatus())))
        .collect(Collectors.groupingBy(d -> d.getDesignationType() + "#" + d.getLanguage())).keySet());
    fields.addAll(concepts.stream().flatMap(c -> c.getVersions().stream()).flatMap(v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream())
        .flatMap(pv -> pv.getEntityPropertyType().equals(EntityPropertyType.coding) ?
            Stream.of(pv.getEntityProperty(), pv.getEntityProperty() + "#system") :
            Stream.of(pv.getEntityProperty()))
        .collect(Collectors.groupingBy(v -> v)).keySet().stream().sorted().toList());
    fields.addAll(Optional.ofNullable(codeSystem.getProperties()).orElse(List.of()).stream()
        .map(PropertyReference::getName)
        .filter(p -> List.of("status", "is-a", "parent", "child", "partOf", "groupedBy", "classifiedWith").contains(p)).toList());
    return fields;
  }

  private Object[] composeRow(Concept c, List<String> headers, Map<String, List<String>> children, Map<String, List<String>> parents) {
    List<Object> row = new ArrayList<>();
    Map<String, List<Designation>> designations = c.getVersions().stream().flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream())
        .filter(d -> PublicationStatus.active.equals(d.getStatus()))
        .collect(Collectors.groupingBy(d -> d.getDesignationType() + "#" + d.getLanguage()));
    Map<String, List<EntityPropertyValue>> properties =
        c.getVersions().stream().flatMap(v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream())
            .flatMap(pv -> pv.getEntityPropertyType().equals(EntityPropertyType.coding) ?
                Stream.of(Pair.of(pv.getEntityProperty(), pv), Pair.of(pv.getEntityProperty() + "#system", pv)) :
                Stream.of(Pair.of(pv.getEntityProperty(), pv)))
            .collect(Collectors.groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));
    headers.forEach(h -> {
      if ("code".equals(h)) {
        row.add(c.getCode());
      } else if ("status".equals(h)) {
        row.add(c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getStatus).orElse(""));
      } else if (List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith").contains(h)) {
        row.add(String.join("#", parents.getOrDefault(c.getCode(), List.of())));
      } else if ("child".equals(h)) {
        row.add(String.join("#", children.getOrDefault(c.getCode(), List.of())));
      } else if (designations.containsKey(h)) {
        row.add(designations.get(h).stream().map(Designation::getName).collect(Collectors.joining("#")));
      } else if (properties.containsKey(h)) {
        row.add(properties.get(h).stream().map(pv -> {
          if (pv.getEntityPropertyType().equals(EntityPropertyType.coding)) {
            return h.contains("#system") ? pv.asCodingValue().getCodeSystem() : pv.asCodingValue().getCode();
          }
          if (pv.getEntityPropertyType().equals(EntityPropertyType.dateTime)) {
            return pv.asDateTimeValue().toLocalDate().toString();
          }
          return pv.getValue() instanceof String ? (String) pv.getValue() : JsonUtil.toJson(pv.getValue());
        }).collect(Collectors.joining("#")));
      } else {
        row.add("");
      }
    });
    return row.toArray();
  }
}
