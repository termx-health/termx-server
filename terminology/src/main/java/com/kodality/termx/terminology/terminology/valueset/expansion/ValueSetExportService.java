package com.kodality.termx.terminology.terminology.valueset.expansion;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.core.utils.CsvUtil;
import com.kodality.termx.core.utils.XlsxUtil;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptUtil;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.valueset.ValueSetSnapshot;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Singleton
@RequiredArgsConstructor
public class ValueSetExportService {
  private final LorqueProcessService lorqueProcessService;
  private final ValueSetVersionService valueSetVersionService;
  private final ConceptService conceptService;
  private final static String process = "vs-expansion-export";

  public LorqueProcess export(String valueSet, String version, String format) {
    Map<String, String> params = Map.of("valueSet", valueSet, "version", version, "format", format);
    return lorqueProcessService.run(process, params, this::composeResult);
  }

  private ProcessResult composeResult(Map<String, String> params) {
    String vsID = params.get("valueSet");
    String vsvVersion = params.get("version");
    ValueSetVersion vsv = valueSetVersionService.load(vsID, vsvVersion).orElseThrow();
    List<ValueSetVersionConcept> concepts = Optional.ofNullable(vsv.getSnapshot()).map(ValueSetSnapshot::getExpansion).orElse(List.of());

    List<String> headers = composeHeaders(concepts, vsv);
    List<Object[]> rows = concepts.stream().map(c -> composeRow(c, headers)).toList();

    String format = params.get("format");
    if ("csv".equals(format)) {
      return ProcessResult.binary(CsvUtil.composeCsv(headers, rows, ";").toString().getBytes());
    }
    if ("xlsx".equals(format)) {
      return ProcessResult.binary(XlsxUtil.composeXlsx(headers, rows, "concepts"));
    }
    throw ApiError.TE807.toApiException();
  }

  private List<String> composeHeaders(List<ValueSetVersionConcept> concepts, ValueSetVersion vsv) {
    List<String> fields = new ArrayList<>();
    fields.add("code");

    List<String> displayLangs = concepts.stream()
        .map(c -> Optional.ofNullable(c.getDisplay()).map(Designation::getLanguage).orElse(null))
        .distinct().toList();
    displayLangs.forEach(l -> fields.add(Stream.of("display", l).filter(Objects::nonNull).collect(Collectors.joining("#"))));

    fields.addAll(concepts.stream()
        .flatMap(c -> Optional.ofNullable(c.getAdditionalDesignations()).orElse(List.of()).stream())
        .collect(Collectors.groupingBy(d -> "additionalDesignation#" + d.getLanguage())).keySet().stream()
        .filter(filed -> fields.stream().noneMatch(f -> f.equals(filed))).toList());

    fields.addAll(concepts.stream().flatMap(v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream())
        .flatMap(pv -> pv.getEntityPropertyType().equals(EntityPropertyType.coding) ?
            Stream.of(pv.getEntityProperty(), pv.getEntityProperty() + "#system", pv.getEntityProperty() + "#display") :
            Stream.of(pv.getEntityProperty()))
        .collect(Collectors.groupingBy(v -> v)).keySet().stream().sorted().toList());

    fields.addAll(vsv.getRuleSet().getRules().stream().flatMap(r -> Optional.ofNullable(r.getProperties()).orElse(List.of()).stream())
        .filter(p -> fields.stream().noneMatch(f -> f.equals(p))).toList());
    fields.add("codeSystem");
    fields.add("codeSystemVersion");
    return fields;
  }

  private Object[] composeRow(ValueSetVersionConcept c, List<String> headers) {
    List<Object> row = new ArrayList<>();
    Map<String, List<Designation>> designations = Optional.ofNullable(c.getAdditionalDesignations()).orElse(List.of()).stream()
        .collect(Collectors.groupingBy(d -> "additionalDesignation#" + d.getLanguage()));
    Map<String, List<EntityPropertyValue>> properties = Optional.ofNullable(c.getPropertyValues()).orElse(List.of()).stream()
        .flatMap(pv -> pv.getEntityPropertyType().equals(EntityPropertyType.coding) ?
            Stream.of(Pair.of(pv.getEntityProperty(), pv), Pair.of(pv.getEntityProperty() + "#system", pv), Pair.of(pv.getEntityProperty() + "#display", pv)) :
            Stream.of(Pair.of(pv.getEntityProperty(), pv)))
        .collect(Collectors.groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));
    headers.forEach(h -> {
      if ("code".equals(h)) {
        row.add(c.getConcept().getCode());
      } else if (c.getDisplay() != null &&
          (Stream.of("display", c.getDisplay().getLanguage()).filter(Objects::nonNull).collect(Collectors.joining("#"))).equals(h)) {
        row.add(c.getDisplay().getName());
      } else if (designations.containsKey(h)) {
        row.add(designations.get(h).stream().map(Designation::getName).collect(Collectors.joining("#")));
      } else if (properties.containsKey(h)) {
        row.add(properties.get(h).stream().map(pv -> {
          if (pv.getEntityPropertyType().equals(EntityPropertyType.coding)) {
            if (h.contains("#system")) {
              return pv.asCodingValue().getCodeSystem();
            }
            if (h.contains("#display")) {
              return conceptService.load(pv.asCodingValue().getCodeSystem(), pv.asCodingValue().getCode())
                  .map(cv -> ConceptUtil.getDisplay(cv.getLastVersion().map(CodeSystemEntityVersion::getDesignations)
                      .orElse(List.of()), SessionStore.require().getLang(), null).getName()).orElse("");
            }
            return pv.asCodingValue().getCode();
          }
          if (pv.getEntityPropertyType().equals(EntityPropertyType.dateTime)) {
            return pv.asDateTimeValue().toLocalDate().toString();
          }
          return pv.getValue() instanceof String ? (String) pv.getValue() : JsonUtil.toJson(pv.getValue());
        }).collect(Collectors.joining("#")));
      } else if (List.of("parent", "groupedBy").contains(h)) {
        row.addAll(Optional.ofNullable(c.getAssociations()).orElse(List.of()).stream().map(CodeSystemAssociation::getTargetCode).toList());
      } else if("codeSystem".equals(h)) {
        row.add(c.getConcept().getBaseCodeSystemUri() != null ? c.getConcept().getBaseCodeSystemUri() : Optional.ofNullable(c.getConcept().getCodeSystemUri()).orElse(""));
      }  else if("codeSystemVersion".equals(h)) {
        row.add(c.getConcept().getCodeSystemVersions() != null ? c.getConcept().getCodeSystemVersions().stream().findFirst().orElse(null) : null);
      } else {
        row.add("");
      }
    });
    return row.toArray();
  }
}
