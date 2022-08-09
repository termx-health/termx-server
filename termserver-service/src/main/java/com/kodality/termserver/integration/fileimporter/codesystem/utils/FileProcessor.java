package com.kodality.termserver.integration.fileimporter.codesystem.utils;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileAnalysisResponse.FileAnalysisProperty;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingProperty;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingResponse.FileProcessingEntityPropertyValue;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingResponse.FileProcessingResponseProperty;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

import static java.util.stream.IntStream.range;

public class FileProcessor {
  public static final String DATE = "dateTime";
  public static final String INTEGER = "integer";
  public static final String TEXT = "string";
  public static final String BOOLEAN = "boolean";
  public static final String DECIMAL = "decimal";

  public static final String IDENTIFIER_PROPERTY = "concept-code";
  private static final List<String> DATE_FORMATS = List.of("dd.MM.yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "dd.MM.yy");


  public FileAnalysisResponse analyze(String type, byte[] file) {
    if (!isUTF8File(file)) {
      throw ApiError.TE701.toApiException();
    }

    RowListProcessor parser = getParser(type, file);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    Map<String, List<String>> columnValues = new HashMap<>();
    range(0, headers.size()).forEach(i -> {
      List<String> vals = rows.stream().map(r -> i < r.length ? r[i] : null).filter(Objects::nonNull).toList();
      columnValues.put(headers.get(i), vals);
    });

    List<FileAnalysisProperty> properties = headers.stream()
        .map(k -> createHeaderProp(k, columnValues))
        .collect(Collectors.toList());

    return new FileAnalysisResponse().setProperties(properties);
  }

  public FileProcessingResponse process(String type, byte[] file, List<FileProcessingProperty> importProperties) {
    if (!isUTF8File(file)) {
      throw ApiError.TE701.toApiException();
    }
    if (importProperties.stream().filter(p -> IDENTIFIER_PROPERTY.equals(p.getName()) && p.isPreferred()).count() > 1) {
      throw ApiError.TE707.toApiException();
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
        if (r[idx] == null) {
          continue;
        }

        FileProcessingEntityPropertyValue ep = mapPropValue(prop, r[idx]);
        var propertyValues = entity.getOrDefault(ep.getPropertyName(), new ArrayList<>());
        propertyValues.add(ep);
        entity.put(ep.getPropertyName(), propertyValues);
      }


      for (FileProcessingProperty prop : identifierProperties) {
        int idx = headers.indexOf(prop.getColumnName());
        if (r[idx] != null) {
          FileProcessingEntityPropertyValue ep = mapPropValue(prop, r[idx]);
          entity.put(ep.getPropertyName(), List.of(ep));
          break;
        }
      }

      return entity;
    }).filter(cs -> !cs.isEmpty() && cs.containsKey(IDENTIFIER_PROPERTY)).toList();


    var properties = importProperties.stream()
        .map(p -> {
          return new FileProcessingResponseProperty()
              .setPropertyName(p.getPropertyName() != null ? p.getPropertyName() : p.getColumnName())
              .setPropertyType(p.getPropertyType());
        })
        .filter(distinctByKey(FileProcessingResponseProperty::getPropertyName))
        .collect(Collectors.toList());


    return new FileProcessingResponse().setEntities(entities).setProperties(properties);
  }


  private FileAnalysisProperty createHeaderProp(String col, Map<String, List<String>> columnValues) {
    String firstColValue = columnValues.get(col).isEmpty() ? null : columnValues.get(col).get(0);
    FileAnalysisProperty prop = new FileAnalysisProperty();
    prop.setColumnName(col);
    prop.setColumnType(getPropertyType(firstColValue));
    prop.setColumnTypeFormat(getDateFormat(firstColValue));
    prop.setHasValues(firstColValue != null);
    return prop;
  }


  private FileProcessingEntityPropertyValue mapPropValue(FileProcessingProperty prop, String rawValue) {
    Object transformedValue = transformPropertyValue(rawValue, prop.getPropertyType(), prop.getPropertyTypeFormat());

    FileProcessingEntityPropertyValue ep = new FileProcessingEntityPropertyValue();
    ep.setColumnName(prop.getColumnName());
    ep.setPropertyName(prop.getName());
    ep.setPropertyType(prop.getPropertyType());
    ep.setPropertyTypeFormat(prop.getPropertyTypeFormat());
    ep.setLang(prop.getLang());
    ep.setValue(transformedValue);
    return ep;
  }


  private Object transformPropertyValue(String val, String type, String dateFormat) {
    if (val == null || type == null) {
      return null;
    }
    return switch (type) {
      case BOOLEAN -> Stream.of("1", "true").anyMatch(v -> v.equalsIgnoreCase(val));
      case INTEGER -> Integer.valueOf(val);
      case DECIMAL -> Double.valueOf(val);
      case DATE -> transformDate(val, dateFormat);
      default -> val;
    };
  }

  public Date transformDate(String date, String format) {
    SimpleDateFormat dateFormat = new SimpleDateFormat(format);
    dateFormat.setLenient(false);
    try {
      return dateFormat.parse(date);
    } catch (ParseException ignored) {
      return null;
    }
  }


  private String getPropertyType(String val) {
    if (val == null) {
      return null;
    }
    if (List.of("0", "1", "false", "true").contains(val)) {
      return BOOLEAN;
    } else if (StringUtils.isNumeric(val)) {
      try {
        Integer.parseInt(val);
        return INTEGER;
      } catch (NumberFormatException ignored) {
      }
      try {
        Double.parseDouble(val);
        return DECIMAL;
      } catch (NumberFormatException ignored) {
      }
    } else if (getDateFormat(val) != null) {
      return DATE;
    }
    return TEXT;
  }

  public String getDateFormat(String date) {
    if (date == null) {
      return null;
    }
    for (String format : DATE_FORMATS) {
      SimpleDateFormat dateFormat = new SimpleDateFormat(format);
      dateFormat.setLenient(false);
      try {
        dateFormat.parse(date.trim());
        return format;
      } catch (ParseException ignored) {
      }
    }
    return null;
  }


  private boolean isUTF8File(byte[] file) {
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    ByteBuffer buf = ByteBuffer.wrap(file);
    try {
      decoder.decode(buf);
      return true;
    } catch (CharacterCodingException e) {
      return false;
    }
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }


  private RowListProcessor getParser(String type, byte[] file) {
    return "tsv".equals(type) ? tsvProcessor(file) : csvProcessor(file);
  }

  private RowListProcessor csvProcessor(byte[] csv) {
    RowListProcessor processor = new RowListProcessor();
    CsvParserSettings settings = new CsvParserSettings();
    settings.setDelimiterDetectionEnabled(true);
    settings.setLineSeparatorDetectionEnabled(true);
    settings.setProcessor(processor);
    settings.setHeaderExtractionEnabled(true);
    new CsvParser(settings).parse(new ByteArrayInputStream(csv));
    return processor;
  }

  private RowListProcessor tsvProcessor(byte[] tsv) {
    RowListProcessor processor = new RowListProcessor();
    TsvParserSettings settings = new TsvParserSettings();
    settings.setLineSeparatorDetectionEnabled(true);
    settings.setProcessor(processor);
    settings.setHeaderExtractionEnabled(true);
    new TsvParser(settings).parse(new ByteArrayInputStream(tsv));
    return processor;
  }
}
