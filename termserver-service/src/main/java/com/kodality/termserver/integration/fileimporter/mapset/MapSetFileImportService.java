package com.kodality.termserver.integration.fileimporter.mapset;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.integration.fileimporter.mapset.utils.MapSetFileImportRequest;
import com.kodality.termserver.integration.fileimporter.mapset.utils.MapSetFileImportRow;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;


@Singleton
@RequiredArgsConstructor
public class MapSetFileImportService {
  private final List<String> VALID_HEADERS = List.of(
      "sourceCodeSystem", "sourceVersion", "sourceCode",
      "targetCodeSystem", "targetVersion", "targetCode",
      "equivalence", "comment",
      "dependsOnProperty", "dependsOnSystem", "dependsOnValue"
  );

  public void process(MapSetFileImportRequest req, byte[] csvFile) {
    List<MapSetFileImportRow> rows = parseRows(csvFile);
    // todo (marina): import entities
  }

  private List<MapSetFileImportRow> parseRows(byte[] csvFile) {
    RowListProcessor parser = csvProcessor(csvFile);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    if (!headers.containsAll(VALID_HEADERS)) {
      String missingHeaders = VALID_HEADERS.stream().filter(h -> !headers.contains(h)).collect(Collectors.joining(", "));
      throw ApiError.TE708.toApiException(Map.of("headers", missingHeaders));
    }

    return rows.stream().map(r -> {
      MapSetFileImportRow row = new MapSetFileImportRow();
      row.setSourceCodeSystem(r[headers.indexOf("sourceCodeSystem")]);
      row.setSourceVersion(r[headers.indexOf("sourceVersion")]);
      row.setSourceCode(r[headers.indexOf("sourceCode")]);

      row.setTargetCodeSystem(r[headers.indexOf("targetCodeSystem")]);
      row.setTargetVersion(r[headers.indexOf("targetVersion")]);
      row.setTargetCode(r[headers.indexOf("targetCode")]);

      row.setEquivalence(r[headers.indexOf("equivalence")]);
      row.setComment(r[headers.indexOf("comment")]);

      row.setDependsOnProperty(r[headers.indexOf("dependsOnProperty")]);
      row.setDependsOnSystem(r[headers.indexOf("dependsOnSystem")]);
      row.setDependsOnValue(r[headers.indexOf("dependsOnValue")]);
      return row;
    }).toList();
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
}
