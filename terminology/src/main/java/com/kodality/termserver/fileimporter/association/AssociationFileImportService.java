package com.kodality.termserver.fileimporter.association;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fileimporter.association.utils.AssociationFileImportRequest;
import com.kodality.termserver.fileimporter.association.utils.AssociationFileImportRow;
import com.kodality.termserver.fileimporter.association.utils.AssociationImportRowMapper;
import com.kodality.termserver.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.univocity.parsers.common.processor.RowListProcessor;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import static com.kodality.termserver.fileimporter.FileParser.csvParser;

@Singleton
@RequiredArgsConstructor
public class AssociationFileImportService {
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final CodeSystemAssociationService codeSystemAssociationService;

  public void process(AssociationFileImportRequest req, byte[] file) {
    Long codeSystemVersionId = req.getCodeSystemVersionId();

    RowListProcessor parser = csvParser(file);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();
    validateRequestColumns(req, headers);

    List<AssociationFileImportRow> importRows = AssociationImportRowMapper.getAssociationFileImportRows(req, headers, rows);
    populateRows(codeSystemVersionId, importRows);
    validateRows(codeSystemVersionId, importRows);

    // map to CS association
    List<CodeSystemAssociation> associations = importRows.stream().map(row -> {
      return new CodeSystemAssociation()
          .setAssociationType(req.getAssociationType())
          .setStatus(PublicationStatus.active)
          .setTargetId(row.getTargetConceptId())
          .setSourceId(row.getSourceConceptId())
          .setOrderNumber(row.getOrder());
    }).toList();

    // load persisted CS version associations
    CodeSystemAssociationQueryParams params = new CodeSystemAssociationQueryParams();
    params.setCodeSystemEntityVersionId(String.valueOf(codeSystemVersionId));
    params.setAssociationType(req.getAssociationType());
    params.all();
    List<CodeSystemAssociation> versionAssociations = codeSystemAssociationService.query(params).getData();
    Set<String> versionAssociationKeys = versionAssociations.stream().map(this::getKey).collect(Collectors.toSet());

    // filter out already existing associations
    List<CodeSystemAssociation> associationsToCreate = associations.stream()
        .filter(ass -> !versionAssociationKeys.contains(getKey(ass))).toList();

    ListUtils.union(versionAssociations, associationsToCreate).stream()
        .collect(Collectors.groupingBy(CodeSystemAssociation::getSourceId))
        .forEach((sId, asses) -> codeSystemAssociationService.save(asses, sId, req.getCodeSystemId()));
  }


  private void validateRequestColumns(AssociationFileImportRequest req, List<String> headers) {
    List<String> requiredHeaders = Arrays.asList(req.getTargetColumn(), req.getSourceColumn(), req.getOrderColumn());
    List<String> missingHeaders = requiredHeaders.stream().filter(h -> !headers.contains(h)).toList();
    if (missingHeaders.size() > 0) {
      throw ApiError.TE708.toApiException(Map.of("headers", StringUtils.join(missingHeaders, ", ")));
    }
  }

  private void populateRows(Long codeSystemVersionId, List<AssociationFileImportRow> importRows) {
    // collect concept codes
    Set<String> targets = importRows.stream().map(AssociationFileImportRow::getTarget).collect(Collectors.toSet());
    Set<String> sources = importRows.stream().map(AssociationFileImportRow::getSource).collect(Collectors.toSet());

    // find concept that exist in provided CS version
    Map<String, Long> targetCodeMapping = loadConcepts(codeSystemVersionId, targets).getData().stream()
        .collect(Collectors.toMap(CodeSystemEntityVersion::getCode, CodeSystemEntityVersion::getId));
    Map<String, Long> sourceCodeMapping = loadConcepts(codeSystemVersionId, sources).getData().stream()
        .collect(Collectors.toMap(CodeSystemEntityVersion::getCode, CodeSystemEntityVersion::getId));

    // populate rows with concept IDs
    importRows.forEach(row -> {
      row.setSourceConceptId(sourceCodeMapping.get(row.getSource()));
      row.setTargetConceptId(targetCodeMapping.get(row.getTarget()));
    });
  }

  private void validateRows(Long codeSystemVersionId, List<AssociationFileImportRow> importRows) {
    // validate that every referenced concept exists in provided CS version
    List<Issue> issues = new ArrayList<>();
    importRows.forEach(row -> {
      if (row.getSourceConceptId() == null) {
        issues.add(ApiError.TE717.toIssue(Map.of("code", row.getSource(), "version", codeSystemVersionId)));
      }
      if (row.getTargetConceptId() == null) {
        issues.add(ApiError.TE718.toIssue(Map.of("code", row.getTarget(), "version", codeSystemVersionId)));
      }
    });

    if (issues.size() > 0) {
      throw new ApiException(400, issues);
    }
  }

  private QueryResult<CodeSystemEntityVersion> loadConcepts(Long codeSystemVersionId, Set<String> codes) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setCodeSystemVersionId(codeSystemVersionId);
    params.setCode(StringUtils.join(codes, ","));
    params.limit(codes.size());
    return codeSystemEntityVersionService.query(params);
  }

  private String getKey(CodeSystemAssociation ass) {
    return StringUtils.join(Arrays.asList(ass.getAssociationType(), ass.getTargetId(), ass.getSourceId()), "#");
  }
}
