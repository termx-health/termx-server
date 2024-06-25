package com.kodality.termx.terminology.fileimporter.mapset;

import com.kodality.commons.model.Issue;
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
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetResourceReference;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetVersionScope;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
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

  public void process(MapSetFileImportRequest req, byte[] file) {
    MapSetImportAction action = new MapSetImportAction();
    action.setActivate(req.getMapSetVersion() != null && PublicationStatus.active.equals(req.getMapSetVersion().getStatus()));
    action.setCleanRun(req.isCleanRun());
    action.setCleanAssociationRun(req.isCleanAssociationRun());

    if (req.getUrl() != null) {
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
    prepare(req, rows);
    save(req, rows, action);
  }

  private void prepare(MapSetFileImportRequest req, List<MapSetFileImportRow> rows) {
    List<Issue> issues = new ArrayList<>();
    MapSetVersionScope scope;
    if (req.getMapSetVersion().getScope() != null) {
      scope = req.getMapSetVersion().getScope();
    } else {
      scope = mapSetVersionService.load(req.getMapSet().getId(), req.getMapSetVersion().getVersion()).map(MapSetVersion::getScope).orElseThrow();
    }

    String sourceType = scope.getSourceType();
    // source code
    if (rows.stream().anyMatch(r -> r.getSourceCode() == null)) {
      issues.add(ApiError.TE729.toIssue(Map.of("property", "sourceCode")));
    }
    // source CodeSystem
    if (rows.stream().anyMatch(r -> r.getSourceCodeSystem() == null)) {
      switch (sourceType) {
        case "code-system" -> {
          if (scope.getSourceCodeSystems().size() != 1) {
            issues.add(ApiError.TE733.toIssue(Map.of("property", "sourceCodeSystem")));
          } else {
            rows.forEach(r -> r.setSourceCodeSystem(scope.getSourceCodeSystems().get(0).getId()));
          }
        }
        case "value-set" -> {
          issues.add(ApiError.TE731.toIssue(Map.of("property", "sourceCodeSystem")));
        }
        case "external-canonical-uri" -> {
          if (scope.getTargetValueSet().getUri() == null) {
            issues.add(ApiError.TE732.toIssue(Map.of("property", "sourceCodeSystem")));
          } else {
            rows.forEach(r -> r.setSourceCodeSystem(scope.getTargetValueSet().getUri()));
          }
        }
        default -> {
          issues.add(ApiError.TE734.toIssue(Map.of("property", "sourceCodeSystem")));
        }
      }
    }
    // source CodeSystem version
    if (rows.stream().anyMatch(r -> r.getSourceVersion() == null)) {
      if ("code-system".equals(sourceType)) {
        List<String> distinctVersions =
            scope.getSourceCodeSystems().stream().map(MapSetResourceReference::getVersion).filter(Objects::nonNull).distinct().toList();
        if (distinctVersions.size() != 1) {
          issues.add(ApiError.TE733.toIssue(Map.of("property", "sourceVersion")));
        } else {
          rows.forEach(r -> r.setSourceVersion(distinctVersions.get(0)));
        }
      } else {
        issues.add(ApiError.TE736.toIssue(Map.of("property", "sourceVersion")));
      }
    }


    String targetType = scope.getTargetType();
    // target code
    if (rows.stream().anyMatch(r -> r.getTargetCode() == null)) {
      issues.add(ApiError.TE729.toIssue(Map.of("property", "targetCode")));
    }
    // target CodeSystem
    if (rows.stream().anyMatch(r -> r.getTargetCodeSystem() == null)) {
      switch (targetType) {
        case "code-system" -> {
          if (scope.getTargetCodeSystems().size() != 1) {
            issues.add(ApiError.TE733.toIssue(Map.of("property", "targetCodeSystem")));
          } else {
            rows.forEach(r -> r.setTargetCodeSystem(scope.getTargetCodeSystems().get(0).getId()));
          }
        }
        case "value-set" -> {
          issues.add(ApiError.TE731.toIssue(Map.of("property", "targetCodeSystem")));
        }
        case "external-canonical-uri" -> {
          if (scope.getTargetValueSet().getUri() == null) {
            issues.add(ApiError.TE732.toIssue(Map.of("property", "targetCodeSystem")));
          } else {
            rows.forEach(r -> r.setTargetCodeSystem(scope.getTargetValueSet().getUri()));
          }
        }
        default -> {
          issues.add(ApiError.TE735.toIssue(Map.of("property", "targetCodeSystem")));
        }
      }
    }
    // target CodeSystem version
    if (rows.stream().anyMatch(r -> r.getTargetVersion() == null)) {
      if ("code-system".equals(targetType)) {
        List<String> distinctVersions =
            scope.getTargetCodeSystems().stream().map(MapSetResourceReference::getVersion).filter(Objects::nonNull).distinct().toList();
        if (distinctVersions.size() != 1) {
          issues.add(ApiError.TE733.toIssue(Map.of("property", "targetVersion")));
        } else {
          rows.forEach(r -> r.setTargetVersion(distinctVersions.get(0)));
        }
      } else {
        issues.add(ApiError.TE737.toIssue(Map.of("property", "targetVersion")));
      }
    }

    // equivalence
    if (rows.stream().anyMatch(r -> r.getEquivalence() == null)) {
      issues.add(ApiError.TE729.toIssue(Map.of("property", "equivalence")));
    }

    if (!issues.isEmpty()) {
      throw ApiError.TE700.toApiException().setIssues(issues);
    }
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

    Set<String> VALID_HEADERS = Set.of("sourceCode", "targetCode", "equivalence");
    if (!headers.containsAll(VALID_HEADERS)) {
      String missingHeaders = VALID_HEADERS.stream().filter(h -> !headers.contains(h)).collect(Collectors.joining(", "));
      throw ApiError.TE708.toApiException(Map.of("headers", missingHeaders));
    }

    return rows.stream().map(r -> {
      MapSetFileImportRow row = new MapSetFileImportRow();
      row.setSourceCodeSystem(getRowValue(headers, r, "sourceCodeSystem"));
      row.setSourceVersion(getRowValue(headers, r, "sourceVersion"));
      row.setSourceCode(getRowValue(headers, r, "sourceCode"));

      row.setTargetCodeSystem(getRowValue(headers, r, "targetCodeSystem"));
      row.setTargetVersion(getRowValue(headers, r, "targetVersion"));
      row.setTargetCode(getRowValue(headers, r, "targetCode"));

      row.setEquivalence(getRowValue(headers, r, "equivalence"));
      row.setComment(getRowValue(headers, r, "comment"));

      row.setDependsOnProperty(getRowValue(headers, r, "dependsOnProperty"));
      row.setDependsOnSystem(getRowValue(headers, r, "dependsOnSystem"));
      row.setDependsOnValue(getRowValue(headers, r, "dependsOnValue"));
      return row;
    }).toList();
  }

  private static String getRowValue(List<String> headers, String[] r, String header) {
    return headers.contains(header) ? r[headers.indexOf(header)] : null;
  }

  public byte[] getTemplate() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CsvWriterSettings settings = new CsvWriterSettings();
    settings.getFormat().setDelimiter(',');
    CsvWriter writer = new CsvWriter(out, settings);

    writer.writeHeaders(List.of("sourceCode", "targetCode", "equivalence", "targetCodeSystem", "targetVersion", "sourceCodeSystem", "sourceVersion", "comment",
        "dependsOnProperty", "dependsOnSystem", "dependsOnValue"));
    writer.writeRowsAndClose(List.of(
        List.of("concept11", "concept21", "source-is-narrower-than-target"),
        List.of("concept12", "concept22", "equivalent"),
        List.of("concept13", "concept23", "source-is-narrower-than-target", "", "", "", "", "comment")
    ));
    return out.toByteArray();
  }
}
