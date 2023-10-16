package com.kodality.termx.terminology.fileimporter.codesystem.utils;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingProperty;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingEntityPropertyValue;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingResponseProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.univocity.parsers.common.processor.RowListProcessor;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kodality.termx.terminology.fileimporter.FileParser.csvParser;
import static com.kodality.termx.terminology.fileimporter.FileParser.tsvParser;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.bool;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.coding;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.dateTime;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.decimal;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.integer;

public class CodeSystemFileImportProcessor {
  public static final String IDENTIFIER_PROPERTY = "concept-code";
  public static final String HIERARCHICAL_CONCEPT = "hierarchical-concept";
  public static final String DESIGNATION_PROPERTY_TYPE = "designation";


  public static CodeSystemFileImportResult process(String type, byte[] file, List<FileProcessingProperty> importProperties) {
    if (importProperties.stream().noneMatch(p -> List.of(IDENTIFIER_PROPERTY, HIERARCHICAL_CONCEPT).contains(p.getName()))) {
      throw ApiError.TE722.toApiException();
    }
    if (importProperties.stream().filter(p -> List.of(IDENTIFIER_PROPERTY, HIERARCHICAL_CONCEPT).contains(p.getName()) && p.isPreferred()).count() > 1) {
      throw ApiError.TE707.toApiException();
    }
    if (importProperties.stream().noneMatch(p -> DESIGNATION_PROPERTY_TYPE.equals(p.getPropertyType()))) {
      throw ApiError.TE721.toApiException();
    }
    if (importProperties.stream().anyMatch(p -> DESIGNATION_PROPERTY_TYPE.equals(p.getPropertyType()) && StringUtils.isEmpty(p.getLanguage()))) {
      throw ApiError.TE728.toApiException();
    }
    importProperties.stream().filter(p -> p.getPropertyType() == null).findFirst().ifPresent(p -> {
      throw ApiError.TE706.toApiException(Map.of("propertyName", p.getName()));
    });

    List<FileProcessingProperty> identifierProperties = importProperties.stream()
        .filter(p -> IDENTIFIER_PROPERTY.equals(p.getName()))
        .sorted((o1, o2) -> Boolean.compare(o2.isPreferred(), o1.isPreferred()))
        .toList();


    RowListProcessor parser = getParser(type, file);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    var entities = rows.stream().map(r -> {
      Map<String, List<FileProcessingEntityPropertyValue>> entity = new HashMap<>();
      for (FileProcessingProperty prop : importProperties) {
        int idx = headers.indexOf(prop.getColumnName());
        if (idx == -1) {
          throw ApiError.TE712.toApiException(Map.of("column", prop.getColumnName()));
        }
        if (idx >= r.length || r[idx] == null) {
          continue;
        }

        List<FileProcessingEntityPropertyValue> values = mapPropValue(prop, r[idx]);
        var propertyValues = entity.getOrDefault(prop.getPropertyName(), new ArrayList<>());
        propertyValues.addAll(values);
        entity.put(prop.getPropertyName(), propertyValues);
      }


      for (FileProcessingProperty prop : identifierProperties) {
        int idx = headers.indexOf(prop.getColumnName());
        if (r[idx] != null) {
          List<FileProcessingEntityPropertyValue> ep = mapPropValue(prop, r[idx]);
          entity.put(prop.getPropertyName(), ep);
          break;
        }
      }

      return entity;
    }).filter(CollectionUtils::isNotEmpty).toList();


    var properties = importProperties.stream()
        .map(p -> {
          return new FileProcessingResponseProperty()
              .setPropertyName(p.getPropertyName() != null ? p.getPropertyName() : p.getColumnName())
              .setPropertyType(DESIGNATION_PROPERTY_TYPE.equals(p.getPropertyType()) ? EntityPropertyType.string : p.getPropertyType())
              .setPropertyKind(DESIGNATION_PROPERTY_TYPE.equals(p.getPropertyType()) ? EntityPropertyKind.designation : EntityPropertyKind.property);
        })
        .filter(distinctByKey(FileProcessingResponseProperty::getPropertyName))
        .collect(Collectors.toList());


    return new CodeSystemFileImportResult().setEntities(entities).setProperties(properties);
  }


  private static List<FileProcessingEntityPropertyValue> mapPropValue(FileProcessingProperty prop, String rawValue) {
    List<String> rowValues = List.of(rawValue);
    if (StringUtils.isNotEmpty(prop.getPropertyDelimiter())) {
      rowValues = Arrays.stream(rawValue.split(Pattern.quote(prop.getPropertyDelimiter()))).map(String::trim).toList();
    }

    return rowValues.stream().map(val -> {
      Object transformedValue = transformPropertyValue(val, prop.getPropertyType(), prop.getPropertyTypeFormat());

      FileProcessingEntityPropertyValue ep = new FileProcessingEntityPropertyValue();
      ep.setColumnName(prop.getColumnName());
      ep.setPropertyName(prop.getName());
      ep.setPropertyType(DESIGNATION_PROPERTY_TYPE.equals(prop.getPropertyType()) ? EntityPropertyType.string : prop.getPropertyType());
      ep.setPropertyTypeFormat(prop.getPropertyTypeFormat());
      ep.setLang(prop.getLanguage());
      ep.setValue(transformedValue);
      return ep;
    }).collect(Collectors.toList());
  }


  private static Object transformPropertyValue(String val, String type, String dateFormat) {
    if (val == null || type == null) {
      return null;
    }
    return switch (type) {
      case bool -> Stream.of("1", "true").anyMatch(v -> v.equalsIgnoreCase(val));
      case integer -> Integer.valueOf(val);
      case decimal -> Double.valueOf(val);
      case dateTime -> transformDate(val, dateFormat);
      case coding -> Map.of("code", val);
      default -> val;
    };
  }

  public static Date transformDate(String date, String format) {
    SimpleDateFormat dateFormat = new SimpleDateFormat(format);
    dateFormat.setLenient(false);
    try {
      return dateFormat.parse(date);
    } catch (ParseException ignored) {
      return null;
    }
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }


  private static RowListProcessor getParser(String type, byte[] file) {
    return "tsv".equals(type) ? tsvParser(file) : csvParser(file);
  }

}
