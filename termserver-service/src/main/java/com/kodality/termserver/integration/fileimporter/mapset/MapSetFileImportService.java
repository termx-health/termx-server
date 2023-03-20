package com.kodality.termserver.integration.fileimporter.mapset;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.mapset.MapSetImportService;
import com.kodality.termserver.integration.fileimporter.mapset.utils.MapSetFileImportRequest;
import com.kodality.termserver.integration.fileimporter.mapset.utils.MapSetFileImportRow;
import com.kodality.termserver.integration.fileimporter.mapset.utils.MapSetFileProcessingMapper;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.mapset.MapSetService;
import com.kodality.termserver.ts.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;


@Singleton
@RequiredArgsConstructor
public class MapSetFileImportService {
  private final MapSetService mapSetService;
  private final MapSetImportService importService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final List<String> VALID_HEADERS = List.of(
      "sourceCodeSystem", "sourceVersion", "sourceCode",
      "targetCodeSystem", "targetVersion", "targetCode",
      "equivalence", "comment",
      "dependsOnProperty", "dependsOnSystem", "dependsOnValue"
  );

  public void process(MapSetFileImportRequest req, byte[] csvFile) {
    List<MapSetFileImportRow> rows = parseRows(csvFile);
    saveProcessingResult(req, rows);
  }

  private void saveProcessingResult(MapSetFileImportRequest req, List<MapSetFileImportRow> rows) {
    MapSetFileProcessingMapper mapper = new MapSetFileProcessingMapper();

    MapSet existingMapSet = mapSetService.load(req.getMap().getId()).orElse(null);
    MapSet mapSet = prepareMapSet(mapper.mapMapSet(req, rows, existingMapSet));
    List<AssociationType> associationTypes = mapper.mapAssociationTypes(rows);
    importService.importMapSet(mapSet, associationTypes, false);
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

  private MapSet prepareMapSet(MapSet mapSet) {
    List<Concept> sourceVSConcepts = valueSetVersionConceptService.expand(mapSet.getSourceValueSet(), null, null).stream().map(ValueSetVersionConcept::getConcept).toList();
    List<Concept> targetVSConcepts = valueSetVersionConceptService.expand(mapSet.getTargetValueSet(), null, null).stream().map(ValueSetVersionConcept::getConcept).toList();
    mapSet.getAssociations().forEach(association -> {
      association.setSource(prepareAssociation(association.getSource(), sourceVSConcepts, mapSet.getAssociations().indexOf(association)));
      association.setTarget(prepareAssociation(association.getTarget(), targetVSConcepts, mapSet.getAssociations().indexOf(association)));
    });
    return mapSet;
  }

  private CodeSystemEntityVersion prepareAssociation(CodeSystemEntityVersion entityVersion, List<Concept> valueSetConcepts, int index) {
    Optional<Concept> concept = valueSetConcepts.stream().filter(vsc -> vsc.getCode().equals(entityVersion.getCode())).findFirst();
    if (concept.isEmpty()) {
      throw ApiError.TE710.toApiException(Map.of("rowNumber", index));
    }
    entityVersion.setCodeSystem(entityVersion.getCodeSystem() == null ? concept.get().getCodeSystem() : entityVersion.getCodeSystem());
    return findEntityVersion(entityVersion, index);
  }

  private CodeSystemEntityVersion findEntityVersion(CodeSystemEntityVersion entityVersion, int index) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setCodeSystem(entityVersion.getCodeSystem());
    params.setCodeSystemVersion(entityVersion.getCodeSystemVersion());
    params.setCode(entityVersion.getCode());
    params.setLimit(1);
    return codeSystemEntityVersionService.query(params).findFirst().orElseThrow(() -> ApiError.TE707.toApiException(Map.of("rowNumber", index)));
  }
}
