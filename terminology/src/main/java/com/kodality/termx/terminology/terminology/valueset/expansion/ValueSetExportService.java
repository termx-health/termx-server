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
import com.kodality.termx.ts.codesystem.Concept;
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
    
    List<Pair<String, String>> codingPairs = collectCodingPropertyPairs(concepts);
    Map<String, Concept> conceptMap = conceptService.batchLoad(codingPairs);
    
    List<Object[]> rows = concepts.stream().map(c -> composeRow(c, headers, conceptMap)).toList();

    String format = params.get("format");
    if ("csv".equals(format)) {
      return ProcessResult.binary(CsvUtil.composeCsv(headers, rows, ";").toString().getBytes());
    }
    if ("xlsx".equals(format)) {
      return ProcessResult.binary(XlsxUtil.composeXlsx(headers, rows, "concepts"));
    }
    throw ApiError.TE807.toApiException();
  }

  private List<Pair<String, String>> collectCodingPropertyPairs(List<ValueSetVersionConcept> concepts) {
    return concepts.stream()
        .flatMap(c -> Optional.ofNullable(c.getPropertyValues()).orElse(List.of()).stream())
        .filter(pv -> pv.getEntityPropertyType().equals(EntityPropertyType.coding))
        .map(pv -> Pair.of(pv.asCodingValue().getCodeSystem(), pv.asCodingValue().getCode()))
        .distinct()
        .toList();
  }

  private List<String> composeHeaders(List<ValueSetVersionConcept> concepts, ValueSetVersion vsv) {
    List<String> fields = new ArrayList<>();
    fields.add("code");

    java.util.Set<String> displayLangs = new java.util.LinkedHashSet<>();
    java.util.Set<String> additionalDesignations = new java.util.LinkedHashSet<>();
    java.util.Set<String> properties = new java.util.TreeSet<>();
    
    int conceptLimit = Math.min(concepts.size(), 1000);
    for (int i = 0; i < conceptLimit; i++) {
      ValueSetVersionConcept c = concepts.get(i);
      
      if (c.getDisplay() != null) {
        String lang = c.getDisplay().getLanguage();
        displayLangs.add(Stream.of("display", lang).filter(Objects::nonNull).collect(Collectors.joining("#")));
      }
      
      Optional.ofNullable(c.getAdditionalDesignations()).orElse(List.of()).forEach(d -> 
          additionalDesignations.add("additionalDesignation#" + d.getLanguage())
      );
      
      Optional.ofNullable(c.getPropertyValues()).orElse(List.of()).forEach(pv -> {
        if (pv.getEntityPropertyType().equals(EntityPropertyType.coding)) {
          properties.add(pv.getEntityProperty());
          properties.add(pv.getEntityProperty() + "#system");
          properties.add(pv.getEntityProperty() + "#display");
        } else {
          properties.add(pv.getEntityProperty());
        }
      });
    }
    
    fields.addAll(displayLangs);
    fields.addAll(additionalDesignations);
    fields.addAll(properties);

    vsv.getRuleSet().getRules().stream()
        .flatMap(r -> Optional.ofNullable(r.getProperties()).orElse(List.of()).stream())
        .filter(p -> !fields.contains(p))
        .forEach(fields::add);
        
    fields.add("codeSystem");
    fields.add("codeSystemVersion");
    return fields;
  }

  private Object[] composeRow(ValueSetVersionConcept c, List<String> headers, Map<String, Concept> conceptMap) {
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
              String key = pv.asCodingValue().getCodeSystem() + "|" + pv.asCodingValue().getCode();
              Concept concept = conceptMap.get(key);
              if (concept != null) {
                Designation display = ConceptUtil.getDisplay(
                    concept.getLastVersion().map(CodeSystemEntityVersion::getDesignations).orElse(List.of()),
                    SessionStore.require().getLang(),
                    null
                );
                return display != null ? display.getName() : "";
              }
              return "";
            }
            return pv.asCodingValue().getCode();
          }
          if (pv.getEntityPropertyType().equals(EntityPropertyType.dateTime)) {
            return pv.asDateTimeValue().toLocalDate().toString();
          }
          return pv.getValue() instanceof String ? (String) pv.getValue() : JsonUtil.toJson(pv.getValue());
        }).collect(Collectors.joining("#")));
      } else if (List.of("parent", "groupedBy").contains(h)) {
        row.add( Optional.ofNullable(c.getAssociations()).map(a -> a.stream().map(CodeSystemAssociation::getTargetCode).collect(Collectors.joining("#"))).orElse(""));
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
