package com.kodality.termx.terminology.codesystem.validator;

import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.MapUtil;
import com.kodality.termx.ApiError;
import com.kodality.termx.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyRule;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import io.micronaut.core.util.CollectionUtils;
import java.security.InvalidParameterException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import static com.kodality.termx.ts.codesystem.EntityPropertyType.bool;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.code;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.coding;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.dateTime;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.decimal;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.integer;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.string;
import static java.util.stream.Collectors.toMap;

@Singleton
@RequiredArgsConstructor
public class CodeSystemValidationService {
  private final Pattern INTEGER_REGEX = Pattern.compile("^\\d+$");
  private final Pattern DECIMAL_REGEX = Pattern.compile("^\\d*\\.?\\d*$");
  private final List<String> VALID_DATE_FORMATS = List.of("yyyy-MM-dd", "dd.MM.yy", "dd.MM.yyyy", "dd/yyyy");

  private final ConceptService conceptService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;


  public List<Issue> validateConcepts(List<Concept> csConcepts, List<EntityProperty> csProperties) {
    Map<String, EntityProperty> csPropMap = csProperties.stream().collect(toMap(EntityProperty::getName, Function.identity()));

    List<String> externalCodings = csConcepts
        .stream()
        .flatMap(c -> c.getVersions().stream())
        .flatMap(v -> v.getPropertyValues().stream())
        .filter(pv -> csPropMap.get(pv.getEntityProperty()) != null && coding.equals(csPropMap.get(pv.getEntityProperty()).getType()))
        .map(EntityPropertyValue::asCodingValue)
        .map(EntityPropertyValueCodingValue::getCode)
        .distinct()
        .toList();

    // entity property name -> concepts[]
    Map<String, List<ValueSetVersionConceptValue>> externalConcepts = csProperties.stream()
        .filter(ep -> coding.equals(ep.getType()))
        .collect(toMap(EntityProperty::getName, ep -> {
          EntityPropertyRule rule = ep.getRule();

          if (StringUtils.isNotBlank(rule.getValueSet())) {
            List<ValueSetVersionConcept> expand = valueSetVersionConceptService.expand(rule.getValueSet(), null);
            return expand.stream().map(ValueSetVersionConcept::getConcept).toList();
          }

          if (CollectionUtils.isNotEmpty(rule.getCodeSystems())) {
            var params = new ConceptQueryParams();
            params.setCode(StringUtils.join(externalCodings, ","));
            params.setLimit(10_000);
            params.setCodeSystem(StringUtils.join(rule.getCodeSystems(), ","));
            return conceptService.query(params).map(ValueSetVersionConceptValue::fromConcept).getData();
          }

          return List.of();
        }));


    return csConcepts.stream()
        .flatMap(concept -> concept.getVersions().stream())
        .flatMap(versionToValidate -> validateConceptVersion(versionToValidate, csProperties, externalConcepts).stream())
        .collect(Collectors.toList());
  }

  private List<Issue> validateConceptVersion(CodeSystemEntityVersion conceptVersion, List<EntityProperty> csProperties, Map<String, List<ValueSetVersionConceptValue>> epExternalConcepts) {
    List<Issue> errs = new ArrayList<>();
    Map<String, EntityProperty> entityPropertyMap = csProperties.stream().collect(toMap(EntityProperty::getName, Function.identity()));

    if (conceptVersion.getCodeSystem() == null) {
      return List.of(ApiError.TE210.toIssue(MapUtil.toMap("code", conceptVersion.getCode())));
    }

    if (conceptVersion.getCode() == null) {
      errs.add(ApiError.TE211.toIssue());
    }

    for (EntityPropertyValue propertyValue : conceptVersion.getPropertyValues()) {
      EntityProperty csEp = entityPropertyMap.get(propertyValue.getEntityProperty());
      errs.addAll(validateEntityPropertyValue(propertyValue, conceptVersion.getCodeSystem(), csEp, epExternalConcepts));
    }

    csProperties.stream().filter(EntityProperty::isRequired).forEach(ep -> {
      boolean designationExists =
          conceptVersion.getDesignations().stream().anyMatch(d -> ep.getName().equals(d.getDesignationType()));
      boolean propertyValueExists =
          conceptVersion.getPropertyValues().stream().anyMatch(pv -> ep.getName().equals(pv.getEntityProperty()));

      if (!designationExists && !propertyValueExists) {
        errs.add(ApiError.TE212.toIssue(MapUtil.toMap("prop", ep.getName())));
      }
    });

    return errs;
  }

  private List<Issue> validateEntityPropertyValue(EntityPropertyValue epv, String epvCodeSystem, EntityProperty ep, Map<String, List<ValueSetVersionConceptValue>> epExternalConcepts) {
    List<Issue> errs = new ArrayList<>();

    // validate entity property existence
    if (ep == null) {
      errs.add(ApiError.TE213.toIssue(MapUtil.toMap("prop", epv.getEntityProperty())));
      return errs;
    }

    // validate value type
    boolean matchesType = isValidEntityPropertyType(epv.getValue(), ep.getType());
    if (!matchesType) {
      errs.add(ApiError.TE214.toIssue(MapUtil.toMap("value", epv.getValue(), "types", ep.getType())));
    }

    // validate Coding type
    if (coding.equals(ep.getType())) {
      EntityPropertyValueCodingValue external = epv.asCodingValue();

      if (external.getCodeSystem() == null) {
        errs.add(ApiError.TE215.toIssue(MapUtil.toMap("code", external.getCode())));
        return errs;
      }
      if (epvCodeSystem.equals(external.getCodeSystem())) {
        errs.add(ApiError.TE216.toIssue(MapUtil.toMap("codeSystem", external.getCodeSystem())));
        return errs;
      }


      List<ValueSetVersionConceptValue> concepts = epExternalConcepts.get(ep.getName()).stream().filter(c -> c.getCode().equals(external.getCode())).toList();
      if (concepts.size() != 1 || !concepts.get(0).getCodeSystem().equals(external.getCodeSystem())) {
        errs.add(ApiError.TE217.toIssue(MapUtil.toMap("codeSystem", external.getCodeSystem(), "code", external.getCode())));
      }
    }
    return errs;
  }


  public boolean isValidEntityPropertyType(Object o, String type) {
    if (type == null) {
      return false;
    }
    if (o == null) {
      return true;
    }

    // concept-property-type
    return switch (type) {
      case string -> true;
      case bool -> o instanceof Boolean;
      case integer -> o instanceof Integer || o instanceof Long || o instanceof String val && INTEGER_REGEX.matcher(val).matches();
      case decimal -> o instanceof Double || o instanceof Float || o instanceof String val && DECIMAL_REGEX.matcher(val).matches();
      case dateTime -> o instanceof Date || o instanceof LocalDate || o instanceof LocalDateTime || o instanceof String val && isValidDate(val);
      case coding -> isCodingValue(o);
      case code -> o instanceof String;
      default -> throw new InvalidParameterException("unknown type %s".formatted(type));
    };
  }

  private boolean isValidDate(String date) {
    return VALID_DATE_FORMATS.stream().anyMatch(f -> {
      try {
        new SimpleDateFormat(f).parse(date);
        return true;
      } catch (ParseException e) {
        return false;
      }
    });
  }

  private boolean isCodingValue(Object o) {
    String json = JsonUtil.toJson(o);
    Map<String, Object> map = JsonUtil.toMap(json);
    return map.keySet().size() == 2 && map.containsKey("code") && map.containsKey("codeSystem");
  }

  private Issue error(String message, Map<String, Object> params) {
    return Issue.error(message, params).setCode("CSVS");
  }
}
