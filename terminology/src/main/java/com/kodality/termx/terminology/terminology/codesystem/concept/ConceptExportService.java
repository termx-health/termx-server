package com.kodality.termx.terminology.terminology.codesystem.concept;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@Singleton
@RequiredArgsConstructor
public class ConceptExportService {
  private final ConceptService conceptService;
  private final LorqueProcessService lorqueProcessService;

  private final static String process = "cs-concept-export";

  public LorqueProcess export(String codeSystem, String version, String format) {
    LorqueProcess lorqueProcess = lorqueProcessService.start(new LorqueProcess().setProcessName(process));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ProcessResult result = ProcessResult.binary(composeResult(codeSystem, version, format));
        lorqueProcessService.complete(lorqueProcess.getId(), result);
      } catch (Exception e) {
        ProcessResult result = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(lorqueProcess.getId(), result);
      }
    }));

    return lorqueProcess;
  }

  private byte[] composeResult(String codeSystem, String version, String format) {
    List<Concept> concepts = conceptService.query(new ConceptQueryParams().setCodeSystem(codeSystem).setCodeSystemVersion(version).all()).getData();

    List<String> headers = composeHeaders(concepts);
    List<Object[]> rows = concepts.stream().map(c -> composeRow(c, headers)).toList();

    if ("csv".equals(format)) {
      return composeCsv(headers, rows);
    }
    if ("xlsx".equals(format)) {
      return composeXlsx(headers, rows);
    }
    throw ApiError.TE807.toApiException();
  }

  private List<String> composeHeaders(List<Concept> concepts) {
    List<String> fields = new ArrayList<>();
    fields.add("code");
    fields.addAll(concepts.stream().flatMap(c -> c.getVersions().stream())
        .flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream().filter(d -> PublicationStatus.active.equals(d.getStatus())))
        .collect(Collectors.groupingBy(d -> d.getDesignationType() + "#" + d.getLanguage())).keySet());
    fields.addAll(concepts.stream().flatMap(c -> c.getVersions().stream()).flatMap(v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream())
        .flatMap(pv -> pv.getEntityPropertyType().equals(EntityPropertyType.coding) ?
            Stream.of(pv.getEntityProperty(), pv.getEntityProperty() + "#system") :
            Stream.of(pv.getEntityProperty()))
        .collect(Collectors.groupingBy(v -> v)).keySet());
    fields.addAll(concepts.stream().flatMap(c -> c.getVersions().stream())
        .flatMap(v -> Optional.ofNullable(v.getAssociations()).orElse(List.of()).stream().filter(d -> PublicationStatus.active.equals(d.getStatus())))
        .collect(Collectors.groupingBy(CodeSystemAssociation::getAssociationType)).keySet());
    return fields;
  }

  private Object[] composeRow(Concept c, List<String> headers) {
    List<Object> row = new ArrayList<>();
    Map<String, List<Designation>> designations = c.getVersions().stream().flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream())
        .filter(d -> PublicationStatus.active.equals(d.getStatus()))
        .collect(Collectors.groupingBy(d -> d.getDesignationType() + "#" + d.getLanguage()));
    Map<String, List<EntityPropertyValue>> properties =
        c.getVersions().stream().flatMap(v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream())
            .flatMap(pv -> pv.getEntityPropertyType().equals(EntityPropertyType.coding) ?
                Stream.of(Pair.of(pv.getEntityProperty(), pv), Pair.of(pv.getEntityProperty() + "#system", pv)) :
                Stream.of(Pair.of(pv.getEntityProperty(), pv)))
            .collect(Collectors.groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));
    Map<String, List<CodeSystemAssociation>> associations = c.getVersions().stream().flatMap(v -> Optional.ofNullable(v.getAssociations()).orElse(List.of()).stream())
        .filter(a -> PublicationStatus.active.equals(a.getStatus()))
        .collect(Collectors.groupingBy(CodeSystemAssociation::getAssociationType));
    headers.forEach(h -> {
      if ("code".equals(h)) {
        row.add(c.getCode());
      } else if (designations.containsKey(h)) {
        row.add(designations.get(h).stream().map(Designation::getName)
            .collect(Collectors.joining("#")));
      } else if (properties.containsKey(h)) {
        row.add(properties.get(h).stream().map(pv -> {
          if (pv.getEntityPropertyType().equals(EntityPropertyType.coding)) {
            return h.contains("#system") ? pv.asCodingValue().getCodeSystem() : pv.asCodingValue().getCode();
          }
          return pv.getValue() instanceof String ? (String) pv.getValue() : JsonUtil.toJson(pv.getValue());
        }).collect(Collectors.joining("#")));
      } else if (associations.containsKey(h)) {
        row.add(associations.get(h).stream().map(CodeSystemAssociation::getTargetCode)
            .collect(Collectors.joining("#")));
      } else {
        row.add("");
      }
    });
    return row.toArray();
  }

  private byte[] composeCsv(List<String> headers, List<Object[]> rows) {
    OutputStream out = new ByteArrayOutputStream();
    CsvWriterSettings settings = new CsvWriterSettings();
    settings.getFormat().setDelimiter(',');
    CsvWriter writer = new CsvWriter(out, settings);
    writer.writeHeaders(headers);
    writer.writeRowsAndClose(rows);
    return out.toString().getBytes();
  }

  private byte[] composeXlsx(List<String> headers, List<Object[]> rows) {

    Workbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("Concepts");

    int rowNum = 0;
    rowNum = addXlsxRow(headers.toArray(), sheet, rowNum);
    for (Object[] row : rows) {
      rowNum = addXlsxRow(row, sheet, rowNum);
    }

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      workbook.write(bos);
      bos.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return bos.toByteArray();
  }

  private int addXlsxRow(Object[] array, Sheet sheet, int rowNum) {
    Row row = sheet.createRow(rowNum);
    int cellNum = 0;
    for (Object o : array) {
      Cell cell = row.createCell(cellNum);
      cell.setCellValue(defaultIfNull(o, "").toString());
      cellNum++;
    }
    rowNum++;
    return rowNum;
  }
}