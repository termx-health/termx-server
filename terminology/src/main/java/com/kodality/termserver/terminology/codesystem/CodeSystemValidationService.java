package com.kodality.termserver.terminology.codesystem;

import com.kodality.commons.model.Issue;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityProperty.EntityPropertyRule;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue;
import io.micronaut.core.util.CollectionUtils;
import java.security.InvalidParameterException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static com.kodality.termserver.ts.codesystem.EntityPropertyType.bool;
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.code;
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.coding;
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.dateTime;
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.decimal;
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.integer;
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.string;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemValidationService {
  private final Pattern INTEGER_REGEX = Pattern.compile("^\\d+$");
  private final Pattern DECIMAL_REGEX = Pattern.compile("^\\d*\\.?\\d*$");
  private final List<String> VALID_DATE_FORMATS = List.of("yyyy-MM-dd", "dd.MM.yy", "dd.MM.yyyy", "dd/yyyy");

  private final ConceptService conceptService;


  public List<Issue> validateConcepts(List<Concept> csConcepts, List<EntityProperty> csProperties) {
    // fixme: more suitable method name. validate assumes throwing an exception?
    List<Concept> unique = csConcepts.stream().collect(collectingAndThen(toCollection(() -> new TreeSet<>(comparing(Concept::getCode))), ArrayList::new));
    Map<String, EntityProperty> csPropMap = csProperties.stream().collect(toMap(EntityProperty::getName, Function.identity()));

    List<String> externalCodings = unique
        .stream()
        .flatMap(c -> c.getVersions().stream())
        .flatMap(v -> v.getPropertyValues().stream())
        .filter(pv -> csPropMap.get(pv.getEntityProperty()) != null && coding.equals(csPropMap.get(pv.getEntityProperty()).getType()))
        .map(EntityPropertyValue::asCodingValue)
        .map(EntityPropertyValueCodingValue::getCode)
        .distinct()
        .toList();

    // entity property name -> concepts[]
    Map<String, List<Concept>> externalConcepts = csProperties.stream()
        .filter(ep -> coding.equals(ep.getType()))
        .collect(toMap(EntityProperty::getName, ep -> {
          EntityPropertyRule rule = ep.getRule();

          var params = new ConceptQueryParams();
          params.setCode(StringUtils.join(externalCodings, ","));
          params.setLimit(10_000);
          if (StringUtils.isNotBlank(rule.getValueSet())) {
            params.setValueSet(rule.getValueSet());
          } else if (CollectionUtils.isNotEmpty(rule.getCodeSystems())) {
            params.setCodeSystem(StringUtils.join(rule.getCodeSystems(), ","));
          }

          QueryResult<Concept> query = conceptService.query(params);
          log.info("concept search for \"{}\" returned: {} concept(s)", ep.getName(), query.getMeta().getTotal());
          return query.getData();
        }));


    return unique.stream()
        .flatMap(concept -> concept.getVersions().stream())
        .flatMap(versionToValidate -> validateConceptVersion(versionToValidate, csProperties, externalConcepts).stream())
        .collect(Collectors.toList());
  }

  private List<Issue> validateConceptVersion(CodeSystemEntityVersion conceptVersion, List<EntityProperty> csProperties,
                                             Map<String, List<Concept>> epExternalConcepts) {
    List<Issue> errs = new ArrayList<>();
    if (conceptVersion.getCodeSystem() == null) {
      return List.of(error("Concept version \"{{code}}\" must reference the codeSystem", nullMap("code", conceptVersion.getCode())));
    }


    Map<String, EntityProperty> entityPropertyMap = csProperties.stream().collect(toMap(EntityProperty::getName, Function.identity()));
    for (EntityPropertyValue propertyValue : conceptVersion.getPropertyValues()) {
      EntityProperty csEp = entityPropertyMap.get(propertyValue.getEntityProperty());
      errs.addAll(validateEntityPropertyValue(propertyValue, conceptVersion.getCodeSystem(), csEp, epExternalConcepts));
    }

    csProperties.stream().filter(EntityProperty::isRequired).forEach(ep -> {
      boolean valueExists = conceptVersion.getPropertyValues().stream().anyMatch(epv -> ep.getName().equals(epv.getEntityProperty()) && epv.getValue() != null);
      if (!valueExists) {
        valueExists = conceptVersion.getDesignations().stream().anyMatch(d -> ep.getName().equals(d.getDesignationType()) && StringUtils.isNotBlank(d.getName()));
      }

      if (!valueExists) {
        errs.add(error("Required property \"{{prop}}\" is missing value(s)", nullMap("prop", ep.getName())));
      }
    });

    return errs;
  }

  private List<Issue> validateEntityPropertyValue(EntityPropertyValue epv, String epvCodeSystem, EntityProperty ep,
                                                  Map<String, List<Concept>> epExternalConcepts) {
    List<Issue> errs = new ArrayList<>();

    // validate entity property existence
    if (ep == null) {
      errs.add(error("Unknown entity property: {{prop}}", nullMap("prop", epv.getEntityProperty())));
      return errs;
    }

    // validate value type
    boolean matchesType = isValidEntityPropertyType(epv.getValue(), ep.getType());
    if (!matchesType) {
      errs.add(error("Value \"{{value}}\" does not match data type \"{{types}}\"", nullMap("value", epv.getValue(), "types", ep.getType())));
    }

    // validate Coding type
    if (coding.equals(ep.getType())) {
      EntityPropertyValueCodingValue external = epv.asCodingValue();

      if (external.getCodeSystem() == null) {
        errs.add(error("Coding \"{{code}}\" is missing the \"codeSystem\" field", nullMap("code", external.getCode())));
        return errs;
      }
      if (epvCodeSystem.equals(external.getCodeSystem())) {
        errs.add(error("Coding \"{{codeSystem}}\" must not reference itself", nullMap("codeSystem", external.getCodeSystem())));
        return errs;
      }


      List<Concept> concept = epExternalConcepts.get(ep.getName()).stream().filter(c -> c.getCode().equals(external.getCode())).toList();
      if (concept.size() != 1 || !concept.get(0).getCodeSystem().equals(external.getCodeSystem())) {
        errs.add(error("Unknown reference \"{{code}}\" to \"{{codeSystem}}\"", nullMap("codeSystem", external.getCodeSystem(), "code", external.getCode())));
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
    return Issue.error(message).setParams(params).setCode("CSVS");
  }

  private <K, V> Map<K, V> nullMap(Object... entries) {
    Map<K, V> m = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      m.put((K) entries[i], (V) entries[i + 1]);
    }
    return m;
  }

}
