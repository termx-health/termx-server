package com.kodality.termx.snomed.snomed.csv;

import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.description.SnomedDescriptionSearchParams;
import com.kodality.termx.snomed.snomed.SnomedService;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import io.micronaut.core.util.CollectionUtils;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Singleton
@RequiredArgsConstructor
public class SnomedConceptCsvService {
  private final static String process = "snomed-concept-csv-export";

  private final LorqueProcessService lorqueProcessService;
  private final SnomedService snomedService;

  public LorqueProcess startCsvExport(SnomedConceptSearchParams params) {
    LorqueProcess lorqueProcess = lorqueProcessService.start(new LorqueProcess().setProcessName(process));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ProcessResult result = ProcessResult.text(composeCsv(params).toString());
        lorqueProcessService.complete(lorqueProcess.getId(), result);
      } catch (Exception e) {
        ProcessResult result = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(lorqueProcess.getId(), result);
      }
    }));

    return lorqueProcess;
  }

  private OutputStream composeCsv(SnomedConceptSearchParams params) {
    params.setAll(true).setLimit(null);
    List<SnomedConcept> concepts = snomedService.searchConcepts(params);
    List<SnomedDescription> descriptions = CollectionUtils.isEmpty(concepts) ? List.of() :
        snomedService.loadDescriptions(concepts.stream().map(SnomedConcept::getConceptId).toList()).stream()
            .filter(d -> d.getTypeId().equals("900000000000013009") && d.getAcceptabilityMap().containsValue("PREFERRED")).toList();
    Map<String, List<SnomedDescription>> conceptDescriptions = descriptions.stream().collect(Collectors.groupingBy(SnomedDescription::getConceptId));
    List<String> langs = descriptions.stream().map(SnomedDescription::getLang).distinct().toList();

    OutputStream out = new ByteArrayOutputStream();
    CsvWriterSettings settings = new CsvWriterSettings();
    settings.getFormat().setDelimiter(',');
    CsvWriter writer = new CsvWriter(out, settings);

    writer.writeHeaders(composeHeaders(langs));
    writer.writeRowsAndClose(concepts.stream().map(c -> composeRow(c, conceptDescriptions.get(c.getConceptId()), langs)).toList());
    return out;
  }

  private List<String> composeHeaders(List<String> langs) {
    List<String> fields = new ArrayList<>();
    fields.add("ConceptId");
    fields.add("Display");
    langs.forEach(l -> fields.add("lang#" + l));
    return fields;
  }

  private Object[] composeRow(SnomedConcept c, List<SnomedDescription> descriptions, List<String> langs) {
    List<Object> row = new ArrayList<>();
    row.add(c.getConceptId());
    row.add(c.getFsn().getTerm());
    langs.forEach(l -> row.add(descriptions.stream().filter(d -> d.getLang().equals(l)).findFirst().map(SnomedDescription::getTerm).orElse(null)));
    return row.toArray();
  }
}
