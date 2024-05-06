package com.kodality.termx.core.sys.checklist.assertion;

import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.checklist.ChecklistService;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.checklist.Checklist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.sys.checklist.ChecklistQueryParams;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Singleton
@RequiredArgsConstructor
public class ChecklistAssertionExportService {
  private final static String process = "checklist-assertion-csv-export";

  private final LorqueProcessService lorqueProcessService;
  private final ChecklistService checklistService;

  public LorqueProcess export(String resourceType, String resourceId, String resourceVersion) {
    LorqueProcess lorqueProcess = lorqueProcessService.start(new LorqueProcess().setProcessName(process));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ProcessResult result = ProcessResult.binary(composeResult(resourceType, resourceId, resourceVersion));
        lorqueProcessService.complete(lorqueProcess.getId(), result);
      } catch (Exception e) {
        ProcessResult result = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(lorqueProcess.getId(), result);
      }
    }));

    return lorqueProcess;
  }

  private byte[] composeResult(String resourceType, String resourceId, String resourceVersion) {
    List<Checklist> checklists = checklistService.query(new ChecklistQueryParams()
        .setResourceType(resourceType)
        .setResourceId(resourceId)
        .setResourceVersion(resourceVersion)
        .setAssertionsDecorated(true).all()).getData();

    List<String> headers = List.of("rule_code", "rule_title", "rule_description", "errors");
    List<Object[]> rows = checklists.stream()
        .filter(checklist -> CollectionUtils.isNotEmpty(checklist.getAssertions()) && !checklist.getAssertions().get(0).isPassed())
        .map(this::composeRow).toList();

    return composeCsv(headers, rows);
  }

  private Object[] composeRow(Checklist checklist) {
    List<Object> row = new ArrayList<>();
    row.add(checklist.getRule().getCode());
    row.add(checklist.getRule().getTitle().getOrDefault(SessionStore.require().getLang(), ""));
    row.add(checklist.getRule().getDescription().getOrDefault(SessionStore.require().getLang(), ""));
    row.add(Optional.ofNullable(checklist.getAssertions().get(0).getErrors()).orElse(List.of()).stream()
        .map(ChecklistAssertionError::getError).collect(Collectors.joining("\n")));
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
}
