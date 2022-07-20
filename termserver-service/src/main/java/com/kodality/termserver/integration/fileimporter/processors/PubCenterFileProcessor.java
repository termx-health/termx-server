package com.kodality.termserver.integration.fileimporter.processors;

import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisResponse.FileAnalyzeProperty;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest.FileProcessProperty;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseProperty;
import com.univocity.parsers.common.processor.RowListProcessor;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.ALIAS;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.DESCRIPTION;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.DESIGNATION;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.DISPLAY;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.IDENTIFIER;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.LEVEL;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.MODIFIED_AT;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.PARENT;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.STATUS;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.VALID_FROM;
import static com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse.FileProcessingResponseCodeSystem.VALID_TO;

@Singleton
public class PubCenterFileProcessor extends FileProcessor {
  private static final List<String> DATE_FORMATS = List.of("dd.MM.yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "dd.MM.yy");

  @Override
  public String getType() {
    return "pub.e-tervis";
  }

  @Override
  public FileAnalysisResponse analyze(String type, byte[] file) {
    RowListProcessor parser = getParser(type, file);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    Map<String, List<String>> colValues = new HashMap<>();
    IntStream.range(0, headers.size()).forEach(i -> {
      List<String> vals = rows.stream().map(r -> r[i]).filter(Objects::nonNull).toList();
      colValues.put(headers.get(i), vals);
    });

    List<FileAnalyzeProperty> properties = headers.stream()
        .map(k -> createHeaderProp(colValues, k))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    return new FileAnalysisResponse().setProperties(properties);
  }


  @Override
  public FileProcessingResponse process(String type, byte[] file, List<FileProcessProperty> importProperties) {
    RowListProcessor parser = getParser(type, file);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();


    var entities = rows.stream().map(r -> {
      Map<String, FileProcessingResponseProperty> cs = new HashMap<>();
      for (FileProcessProperty prop : importProperties) {
        int idx = headers.indexOf(prop.getColumnName());
        if (r[idx] == null) {
          continue;
        }

        String rawValue = r[idx];
        Object transformedValue = transformPropertyValue(rawValue, prop.getPropertyType(), prop.getTypeFormat());

        FileProcessingResponseProperty rp = new FileProcessingResponseProperty();
        rp.setColumnName(prop.getColumnName());
        rp.setPropertyType(prop.getPropertyType());
        rp.setTypeFormat(prop.getTypeFormat());
        rp.setLang(prop.getLang());
        rp.setValue(transformedValue);

        cs.put(prop.getMappedProperty(), rp);
      }
      return cs;
    }).filter(cs -> !cs.isEmpty()).toList();

    return new FileProcessingResponse(entities);
  }

  private RowListProcessor getParser(String type, byte[] file) {
    return "tsv".equals(type) ? tsvProcessor(file) : csvProcessor(file);
  }


  private FileAnalyzeProperty createHeaderProp(Map<String, List<String>> headerCells, String col) {
    if (getMappedProperty(col) == null) {
      return null;
    }

    String firstHeaderVal = headerCells.get(col).isEmpty() ? null : headerCells.get(col).get(0);
    FileAnalyzeProperty prop = new FileAnalyzeProperty();
    prop.setColumnName(col);
    prop.setMappedProperty(getMappedProperty(col));
    prop.setPropertyType(getPropertyType(firstHeaderVal));
    prop.setTypeFormat(getDateFormat(firstHeaderVal));
    prop.setHasValues(firstHeaderVal != null);
    return prop;
  }


  private String getMappedProperty(String prop) {
    Map<String, String> props = new HashMap<>();
    props.put("id ", IDENTIFIER);
    props.put("code ", IDENTIFIER);
    props.put("identifier ", IDENTIFIER);
    props.put("kood", IDENTIFIER);
    props.put("lühinimetus", ALIAS);
    props.put("nimetus", DISPLAY);
    props.put("pikk_nimetus", DESIGNATION);
    props.put("vanem_kood", PARENT);
    props.put("hierarhia_aste", LEVEL);
    props.put("kehtivuse_alguse_kpv", VALID_FROM);
    props.put("kehtivuse_lõpu_kpv", VALID_TO);
    props.put("viimane_muudatus_kpv", MODIFIED_AT);
    props.put("staatus", STATUS);
    props.put("selgitus", DESCRIPTION);
    return props.get(prop.toLowerCase());
  }

  private String getPropertyType(String val) {
    if (val == null) {
      return null;
    }
    if (List.of("0", "1", "false", "true").contains(val)) {
      return BOOLEAN;
    } else if (StringUtils.isNumeric(val)) {
      return INTEGER;
    } else if (getDateFormat(val) != null) {
      return DATE;
    } else {
      return TEXT;
    }
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

  private Object transformPropertyValue(String val, String type, String dateFormat) {
    if (val == null) {
      return null;
    }
    return switch (type) {
      case BOOLEAN -> List.of("1", "true").contains(val);
      case INTEGER -> Integer.valueOf(val);
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
}
