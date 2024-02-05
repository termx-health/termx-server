package com.kodality.termx.terminology.fileimporter.valueset.utils;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.univocity.parsers.common.processor.RowListProcessor;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.kodality.termx.terminology.fileimporter.FileParser.csvParser;
import static com.kodality.termx.terminology.fileimporter.FileParser.tsvParser;

public class ValueSetFileImportProcessor {

  public static List<ValueSetVersionConcept> process(ValueSetFileImportRequest request, ValueSetVersionRule existingRule, byte[] file) {
    String type = request.getType();

    RowListProcessor parser = getParser(type, file);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    String cs = request.getVersion() != null && request.getVersion().getRule() != null && request.getVersion().getRule().getCodeSystem() != null ?
        request.getVersion().getRule().getCodeSystem() : existingRule != null ? existingRule.getCodeSystem() : null;

    if (CollectionUtils.isNotEmpty(rows) && cs == null) {
      throw ApiError.TE726.toApiException();
    }

    Integer codeIdx = headers.indexOf(request.getMapping().getCode());
    Integer displayIdx = request.getMapping().getDisplay() == null ? null : headers.indexOf(request.getMapping().getDisplay());
    Integer retirementDateIdx = request.getMapping().getRetirementDate() == null ? null : headers.indexOf(request.getMapping().getRetirementDate());
    Integer statusIdx = request.getMapping().getStatus() == null ? null : headers.indexOf(request.getMapping().getStatus());
    return rows.stream()
        .filter(r -> (retirementDateIdx == null || !StringUtils.isNotEmpty(r[retirementDateIdx])) &&
            (statusIdx == null || !PublicationStatus.retired.equals(PublicationStatus.getStatus(r[statusIdx]))))
        .map(r -> {
          if (r.length > (codeIdx + 1) && r[codeIdx] == null) {
            return null;
          }
          ValueSetVersionConcept concept = new ValueSetVersionConcept();
          concept.setConcept(new ValueSetVersionConceptValue().setCode(r[codeIdx]).setCodeSystem(cs));
          if (displayIdx != null && r.length > (displayIdx + 1) && r[displayIdx] != null) {
            concept.setDisplay(new Designation().setName(r[displayIdx]));
          }
          return concept;
        }).filter(Objects::nonNull).toList();
  }

  private static RowListProcessor getParser(String type, byte[] file) {
    return "tsv".equals(type) ? tsvParser(file) : csvParser(file);
  }

}
