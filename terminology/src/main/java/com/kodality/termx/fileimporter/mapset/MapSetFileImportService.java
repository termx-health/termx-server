package com.kodality.termx.fileimporter.mapset;

import com.kodality.termx.ApiError;
import com.kodality.termx.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termx.fileimporter.mapset.utils.MapSetFileImportRequest;
import com.kodality.termx.fileimporter.mapset.utils.MapSetFileImportRow;
import com.kodality.termx.fileimporter.mapset.utils.MapSetFileProcessingMapper;
import com.kodality.termx.terminology.mapset.MapSetImportService;
import com.kodality.termx.terminology.mapset.MapSetService;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.mapset.MapSet;
import com.univocity.parsers.common.processor.RowListProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import static com.kodality.termx.fileimporter.FileParser.csvParser;


@Singleton
@RequiredArgsConstructor
public class MapSetFileImportService {
  private final MapSetService mapSetService;
  private final MapSetImportService importService;
  private final ConceptMapFhirImportService fhirImportService;

  private final List<String> VALID_HEADERS = List.of(
      "sourceCodeSystem", "sourceVersion", "sourceCode",
      "targetCodeSystem", "targetVersion", "targetCode",
      "equivalence", "comment",
      "dependsOnProperty", "dependsOnSystem", "dependsOnValue"
  );

  public void process(MapSetFileImportRequest req, byte[] file) {
    if ("json".equals(req.getType())) {
      fhirImportService.importMapSet(new String(file, StandardCharsets.UTF_8), req.getMap().getId());
      return;
    } //TODO fsh file import

    List<MapSetFileImportRow> rows = parseRows(file);
    saveProcessingResult(req, rows);
  }

  private void saveProcessingResult(MapSetFileImportRequest req, List<MapSetFileImportRow> rows) {
    MapSetFileProcessingMapper mapper = new MapSetFileProcessingMapper();

    MapSet existingMapSet = mapSetService.load(req.getMap().getId()).orElse(null);
    MapSet mapSet = mapper.mapMapSet(req, rows, existingMapSet);
    List<AssociationType> associationTypes = mapper.mapAssociationTypes(rows);
    importService.importMapSet(mapSet, associationTypes);
  }

  private List<MapSetFileImportRow> parseRows(byte[] csvFile) {
    RowListProcessor parser = csvParser(csvFile);
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
}
