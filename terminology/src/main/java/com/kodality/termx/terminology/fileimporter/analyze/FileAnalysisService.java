package com.kodality.termx.terminology.fileimporter.analyze;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.fileimporter.analyze.FileAnalysisResponse.FileAnalysisColumn;
import com.kodality.termx.core.http.BinaryHttpClient;
import com.kodality.termx.terminology.fileimporter.fileparser.FileParserFactory;
import com.kodality.termx.terminology.fileimporter.fileparser.IFileParser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static java.util.stream.IntStream.range;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class FileAnalysisService {
  // fixme: The CS import includes entity property types, but in other contexts, it could be something different.
  //  Should this service have its own types? The mapping between different types should be performed in the places where the analysis response is utilized.
  private static String string = "string";
  private static String bool = "boolean";
  private static String dateTime = "dateTime";
  private static String decimal = "decimal";
  private static String integer = "integer";
  private static final List<String> DATE_FORMATS = List.of("dd.MM.yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "dd.MM.yy");
  private static final List<String> SUPPORTED_TYPES = List.of("csv", "tsv", "xlsx");

  private final BinaryHttpClient client = new BinaryHttpClient();


  public FileAnalysisResponse analyze(FileAnalysisRequest request) {
    byte[] file = loadFile(request.getLink());
    return analyze(request, file);
  }

  public FileAnalysisResponse analyze(FileAnalysisRequest request, byte[] file) {
    return analyze(request.getType(), file);
  }

  public static FileAnalysisResponse analyze(String type, byte[] file) {
    if (!SUPPORTED_TYPES.contains(type)) {
      throw new IllegalArgumentException("unknown type: " + type);
    }

    IFileParser parser = FileParserFactory.getParser(type, file);
    List<String> headers = parser.getHeaders();
    List<String[]> rows = parser.getRows();

    Map<String, List<String>> columnValues = new HashMap<>();
    range(0, headers.size()).forEach(i -> {
      List<String> vals = rows.stream().map(r -> i < r.length ? r[i] : null).filter(Objects::nonNull).toList();
      columnValues.put(headers.get(i), vals);
    });

    List<FileAnalysisColumn> properties = headers.stream().map(k -> createHeaderProp(k, columnValues.get(k))).collect(Collectors.toList());

    return new FileAnalysisResponse().setColumns(properties);
  }


  private static FileAnalysisColumn createHeaderProp(String col, List<String> columnValues) {
    // fixme: It could be empty, which will not allow for the correct determination of type and format.
    String firstColValue = columnValues.isEmpty() ? null : columnValues.get(0);
    FileAnalysisColumn prop = new FileAnalysisColumn();
    prop.setColumnName(col);
    prop.setHasValues(columnValues.stream().anyMatch(StringUtils::isNotBlank));
    prop.setColumnType(getPropertyType(firstColValue));
    prop.setColumnTypeFormat(getDateFormat(firstColValue));
    return prop;
  }


  private static String getPropertyType(String val) {
    // fixme: Is it possible to make this configurable/extendable? In other situations, a different approach could be utilized to determine a type, or there may even be a new type added.
    if (val == null) {
      return null;
    }
    if (Stream.of("0", "1", "false", "true", "F", "T").anyMatch(v -> v.equalsIgnoreCase(val))) {
      return bool;
    } else if (StringUtils.isNumeric(val)) {
      try {
        Integer.parseInt(val);
        return integer;
      } catch (NumberFormatException ignored) {
      }
      try {
        Double.parseDouble(val);
        return decimal;
      } catch (NumberFormatException ignored) {
      }
    } else if (getDateFormat(val) != null) {
      return dateTime;
    }
    return string;
  }

  private static String getDateFormat(String date) {
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

  // utils

  private byte[] loadFile(String link) {
    try {
      return client.GET(link).body();
    } catch (Exception e) {
      throw ApiError.TE711.toApiException();
    }
  }
}
