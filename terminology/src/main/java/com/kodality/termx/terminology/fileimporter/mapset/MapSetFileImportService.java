package com.kodality.termx.terminology.fileimporter.mapset;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.fhir.FhirFshConverter;
import com.kodality.termx.terminology.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termx.terminology.fileimporter.mapset.utils.MapSetFileImportRequest;
import com.kodality.termx.terminology.fileimporter.mapset.utils.MapSetFileImportRow;
import com.kodality.termx.terminology.fileimporter.mapset.utils.MapSetFileProcessingMapper;
import com.kodality.termx.terminology.terminology.mapset.MapSetImportService;
import com.kodality.termx.terminology.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetImportAction;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.univocity.parsers.common.processor.RowListProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import static com.kodality.termx.terminology.fileimporter.FileParser.csvParser;


@Singleton
@RequiredArgsConstructor
public class MapSetFileImportService {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetImportService importService;
  private final ConceptMapFhirImportService fhirImportService;
  private final Optional<FhirFshConverter> fhirFshConverter;

  private final List<String> VALID_HEADERS = List.of(
      "sourceCodeSystem", "sourceVersion", "sourceCode",
      "targetCodeSystem", "targetVersion", "targetCode",
      "equivalence", "comment",
      "dependsOnProperty", "dependsOnSystem", "dependsOnValue"
  );

  public void process(MapSetFileImportRequest req, byte[] file) {
    MapSetImportAction action = new MapSetImportAction();
    action.setActivate(req.getMapSetVersion() != null && PublicationStatus.active.equals(req.getMapSetVersion().getStatus()));
    action.setCleanRun(req.isCleanRun());
    action.setCleanAssociationRun(req.isCleanAssociationRun());

    if (req.getUrl() != null ) {
      fhirImportService.importMapSetFromUrl(req.getUrl(), req.getMapSet().getId(), action);
      return;
    }
    if ("json".equals(req.getType())) {
      fhirImportService.importMapSet(new String(file, StandardCharsets.UTF_8), req.getMapSet().getId(), action);
      return;
    }
    if ("fsh".equals(req.getType())) {
      String json = fhirFshConverter.orElseThrow(ApiError.TE806::toApiException).toFhir(new String(file, StandardCharsets.UTF_8)).join();
      fhirImportService.importMapSet(json, req.getMapSet().getId(), action);
    }

    List<MapSetFileImportRow> rows = parseRows(file);
    save(req, rows, action);
  }

  private void save(MapSetFileImportRequest req, List<MapSetFileImportRow> rows, MapSetImportAction action) {
    MapSetFileProcessingMapper mapper = new MapSetFileProcessingMapper();
    MapSet existingMapSet = mapSetService.load(req.getMapSet().getId()).orElse(null);
    MapSetVersion existingMapSetVersion = mapSetVersionService.load(req.getMapSet().getId(), req.getMapSetVersion().getVersion()).orElse(null);
    MapSet mapSet = mapper.mapMapSet(req, rows, existingMapSet, existingMapSetVersion);
    List<AssociationType> associationTypes = mapper.mapAssociationTypes(rows);

    importService.importMapSet(mapSet, associationTypes, action);
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
