package com.kodality.termx.core.sys.release.notes;

import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.release.ReleaseRepository;
import com.kodality.termx.core.ts.CodeSystemProvider;
import com.kodality.termx.core.ts.ValueSetProvider;
import com.kodality.termx.core.utils.TranslateUtil;
import com.kodality.termx.sys.release.Release;
import com.kodality.termx.sys.release.ReleaseResource;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemCompareResult;
import com.kodality.termx.ts.codesystem.CodeSystemCompareResult.CodeSystemCompareResultChange;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetCompareResult;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionReference;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@RequiredArgsConstructor
public class ReleaseNotesService {
  private final ReleaseRepository releaseRepository;
  private final CodeSystemProvider codeSystemProvider;
  private final ValueSetProvider valueSetProvider;
  private final ReleaseAttachmentService attachmentService;
  private final ProvenanceService provenanceService;

  private static final String i18n = "i18n.note";

  public void generateNotes(Long releaseId) {
    Release release = releaseRepository.load(releaseId);
    generateNotes(release);
  }

  public void generateNotes(Release release) {
    Map<CodeSystem, CodeSystemCompareResult> codeSystemCompareResults = getCodeSystemCompareResults(release);
    Map<ValueSet, ValueSetCompareResult> valueSetCompareResults = getValueSetCompareResults(release);

    Attachment csv = composeCsv(codeSystemCompareResults, valueSetCompareResults)
        .setFileName(release.getCode() + ".csv");
    Attachment txt = composeTxt(release, codeSystemCompareResults, valueSetCompareResults)
        .setFileName(release.getCode() + ".txt");

    attachmentService.saveAttachments(release.getId(), Map.of(
        release.getCode() + ".csv", csv,
        release.getCode() + ".txt", txt
    ));
  }

  private Attachment composeCsv(Map<CodeSystem, CodeSystemCompareResult> csCompare, Map<ValueSet, ValueSetCompareResult> vsCompare) {
    Attachment attachment = new Attachment().setContentType("text/csv");

    OutputStream out = new ByteArrayOutputStream();
    CsvWriterSettings settings = new CsvWriterSettings();
    settings.getFormat().setDelimiter(';');

    CsvWriter csvWriter = new CsvWriter(out, settings);
    csvWriter.writeHeaders("Type", "URI", "Version", "Title", "Name", "EffectivePeriod", "ChangeDate", "Code", "Display", "ChangeType", "Changes", "Comments");

    List<Object[]> rows = new ArrayList<>();
    rows.addAll(csCompare.entrySet().stream().flatMap(this::composeCsCsvRow).toList());
    rows.addAll(vsCompare.entrySet().stream().flatMap(this::composeVsCsvRow).toList());
    csvWriter.writeRowsAndClose(rows);

    attachment.setContent(out.toString().getBytes());
    attachment.setContentLength((long) attachment.getContent().length);
    return attachment;
  }

  private Attachment composeTxt(Release release, Map<CodeSystem, CodeSystemCompareResult> csCompare, Map<ValueSet, ValueSetCompareResult> vsCompare) {
    Attachment attachment = new Attachment().setContentType("text/plain");

    StringBuilder sb = new StringBuilder();
    sb.append(TranslateUtil.translate("txt.name", i18n)).append(": ")
        .append(release.getCode())
        .append(" ")
        .append(release.getNames().getOrDefault(SessionStore.require().getLang(), release.getNames().values().stream().findFirst().orElse("")))
        .append("\n");
    sb.append(TranslateUtil.translate("txt.date", i18n)).append(": ").append(release.getReleaseDate().toLocalDate().toString());

    Map<String, String> txtBlocks = new HashMap<>();
    txtBlocks.putAll(composeCsTxt(csCompare));
    txtBlocks.putAll(composeVsTxt(vsCompare));
    txtBlocks.putAll(composeResourceTxt(release.getResources()));

    txtBlocks.entrySet().stream().sorted(Entry.comparingByKey()).forEach(es -> sb.append(es.getValue()));

    attachment.setContent(sb.toString().getBytes());
    attachment.setContentLength((long) attachment.getContent().length);
    return attachment;
  }

  // ------------------------------ CS ------------------------------
  private Map<CodeSystem, CodeSystemCompareResult> getCodeSystemCompareResults(Release release) {
    return release.getResources().stream()
        .filter(r -> "CodeSystem".equals(r.getResourceType()))
        .map(r -> codeSystemProvider.compareWithPreviousVersion(r.getResourceId(), r.getResourceVersion()))
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  private Stream<Object[]> composeCsCsvRow(Entry<CodeSystem, CodeSystemCompareResult> entry) {
    CodeSystemVersion csv = entry.getKey().getLastVersion().get();
    Map<String, Concept> concepts = codeSystemProvider.searchConcepts(new ConceptQueryParams()
            .setCodeSystem(csv.getCodeSystem())
            .setCodes(entry.getValue().affectedCodes())
            .limit(entry.getValue().affectedCodes().size()))
        .getData().stream().collect(Collectors.toMap(Concept::getCode, c -> c));

    List<Object> commonPart = csRowCommonPart(entry, csv);

    List<Object[]> rows = new ArrayList<>();
    rows.addAll(entry.getValue().getAdded().stream().map(code -> csRows(commonPart, concepts.get(code), "added")).toList());
    rows.addAll(entry.getValue().getChanged().stream()
        .filter(CodeSystemCompareResultChange::retired)
        .map(res -> csRows(commonPart, concepts.get(res.getCode()), "retired")).toList());
    rows.addAll(entry.getValue().getChanged().stream()
        .filter(CodeSystemCompareResultChange::contentChanged)
        .map(res -> {
          Object[] row = csRows(commonPart, concepts.get(res.getCode()), "changed");
          row[10] = csRowChange(res);
          return row;
        }).toList());
    rows.addAll(entry.getValue().getDeleted().stream().map(code -> csRows(commonPart, concepts.get(code), "removed")).toList());
    return rows.stream();
  }

  private List<Object> csRowCommonPart(Entry<CodeSystem, CodeSystemCompareResult> entry, CodeSystemVersion csv) {
    List<Provenance> provenances = provenanceService.find("CodeSystemVersion|" + csv.getId());

    List<Object> row = new ArrayList<>();
    row.add("CodeSystem");
    row.add(entry.getKey().getUri());
    row.add(csv.getVersion());
    row.add(entry.getKey().getTitle().getOrDefault(csv.getPreferredLanguage(), entry.getKey().getTitle().values().stream().findFirst().orElse("")));
    row.add(entry.getKey().getName());
    row.add(String.join(" - ",
        Optional.ofNullable(csv.getReleaseDate()).map(LocalDate::toString).orElse("..."),
        Optional.ofNullable(csv.getExpirationDate()).map(LocalDate::toString).orElse("...")));
    row.add(getLatestProvenanceDate(provenances));
    return row;
  }

  private static Object[] csRows(List<Object> commonPart, Concept concept, String changeType) {
    List<Object> row = new ArrayList<>(commonPart);
    row.add(concept.getCode());
    row.add(concept.getLastVersion().map(v -> v.getDesignations().stream().filter(Designation::isPreferred).findFirst().map(Designation::getName).orElse(""))
        .orElse(""));
    row.add(changeType);
    row.add("");
    row.add(concept.getLastVersion().map(CodeSystemEntityVersion::getDescription).orElse(""));
    return row.toArray();
  }

  private static String csRowChange(CodeSystemCompareResultChange res) {
    String properties = csRowChange(res.getDiff().getOld().getProperties(), res.getDiff().getMew().getProperties());
    String designations = csRowChange(res.getDiff().getOld().getDesignations(), res.getDiff().getMew().getDesignations());
    return Stream.of(properties, designations).filter(StringUtils::isNotEmpty).collect(Collectors.joining("\n"));
  }

  private static String csRowChange(List<String> source, List<String> target) {
    List<String> old = Optional.ofNullable(source).orElse(List.of());
    List<String> mew = Optional.ofNullable(target).orElse(List.of());
    String removed = old.stream().filter(o -> mew.stream().noneMatch(m -> m.equals(o))).map(o -> "- " + o).collect(Collectors.joining("\n"));
    String added = mew.stream().filter(m -> old.stream().noneMatch(o -> o.equals(m))).map(m -> "+ " + m).collect(Collectors.joining("\n"));
    return Stream.of(removed, added).filter(StringUtils::isNotEmpty).collect(Collectors.joining("\n"));
  }

  private Map<String, String> composeCsTxt(Map<CodeSystem, CodeSystemCompareResult> res) {
    return res.entrySet().stream().map(es -> {
      StringBuilder sb = new StringBuilder();
      sb.append("\n\n");
      sb.append(TranslateUtil.translate("txt.codesystem", i18n)).append(": ")
          .append(es.getKey().getTitle().getOrDefault(SessionStore.require().getLang(), es.getKey().getTitle().values().stream().findFirst().orElse("")))
          .append("\n");
      sb.append(TranslateUtil.translate("txt.url", i18n)).append(": ").append(es.getKey().getUri()).append("\n");

      sb.append(TranslateUtil.translate("txt.changes", i18n)).append(": ")
          .append(es.getKey().getFirstVersion().map(CodeSystemVersionReference::getVersion).orElse(""))
          .append(" -> ")
          .append(es.getKey().getLastVersion().map(CodeSystemVersionReference::getVersion).orElse(""))
          .append(" ");
      if (!es.getValue().getAdded().isEmpty()) {
        sb.append(TranslateUtil.translate("txt.added", i18n)).append(":")
            .append(es.getValue().getAdded().size()).append("; ");
      }
      if (es.getValue().getChanged().stream().anyMatch(CodeSystemCompareResultChange::retired)) {
        sb.append(TranslateUtil.translate("txt.retired", i18n)).append(":")
            .append(es.getValue().getChanged().stream().filter(CodeSystemCompareResultChange::retired).toList().size()).append("; ");
      }
      if (es.getValue().getChanged().stream().anyMatch(CodeSystemCompareResultChange::contentChanged)) {
        sb.append(TranslateUtil.translate("txt.changed", i18n)).append(":")
            .append(es.getValue().getChanged().stream().filter(CodeSystemCompareResultChange::contentChanged).toList().size()).append("; ");
      }
      if (!es.getValue().getDeleted().isEmpty()) {
        sb.append(TranslateUtil.translate("txt.removed", i18n)).append(":").append(es.getValue().getDeleted().size()).append("; ");
      }
      String description = es.getKey().getLastVersion()
          .map(CodeSystemVersion::getDescription)
          .map(d -> d.getOrDefault(SessionStore.require().getLang(), d.values().stream().findFirst().orElse(""))).orElse("");
      if (StringUtils.isNotEmpty(description)) {
        sb.append("\n").append(TranslateUtil.translate("txt.description", i18n)).append(": ").append(description);
      }
      return Pair.of(es.getKey().getId() + "cs", sb.toString());
    }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  // ------------------------------ VS ------------------------------
  private Map<ValueSet, ValueSetCompareResult> getValueSetCompareResults(Release release) {
    return release.getResources().stream()
        .filter(r -> "ValueSet".equals(r.getResourceType()))
        .map(r -> valueSetProvider.compareWithPreviousVersion(r.getResourceId(), r.getResourceVersion()))
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  private Stream<Object[]> composeVsCsvRow(Entry<ValueSet, ValueSetCompareResult> entry) {
    ValueSetVersion vsv = entry.getKey().getLastVersion().get();
    List<Object> commonPart = vsRowCommonPart(entry, vsv);
    Map<String, ValueSetVersionConcept> oldConcepts =
        entry.getKey().getFirstVersion().get().getSnapshot().getExpansion().stream().collect(Collectors.toMap(c -> c.getConcept().getCode(), c -> c));
    Map<String, ValueSetVersionConcept> newConcepts =
        vsv.getSnapshot().getExpansion().stream().collect(Collectors.toMap(c -> c.getConcept().getCode(), c -> c));

    List<Object[]> rows = new ArrayList<>();
    rows.addAll(entry.getValue().getAdded().stream().map(code -> vsRows(commonPart, newConcepts.get(code), "added")).toList());
    rows.addAll(entry.getValue().getDeleted().stream().map(code -> vsRows(commonPart, oldConcepts.get(code), "removed")).toList());
    return rows.stream();
  }

  private List<Object> vsRowCommonPart(Entry<ValueSet, ValueSetCompareResult> entry, ValueSetVersion vsv) {
    List<Provenance> provenances = provenanceService.find("ValueSetVersion|" + vsv.getId());

    List<Object> row = new ArrayList<>();
    row.add("ValueSet");
    row.add(entry.getKey().getUri());
    row.add(vsv.getVersion());
    row.add(entry.getKey().getTitle().getOrDefault(vsv.getPreferredLanguage(), entry.getKey().getTitle().values().stream().findFirst().orElse("")));
    row.add(entry.getKey().getName());
    row.add(String.join(" - ",
        Optional.ofNullable(vsv.getReleaseDate()).map(LocalDate::toString).orElse("..."),
        Optional.ofNullable(vsv.getExpirationDate()).map(LocalDate::toString).orElse("...")));
    row.add(getLatestProvenanceDate(provenances));
    return row;
  }

  private static Object[] vsRows(List<Object> commonPart, ValueSetVersionConcept concept, String changeType) {
    List<Object> row = new ArrayList<>(commonPart);
    row.add(concept.getConcept().getCode());
    row.add(Optional.ofNullable(concept.getDisplay()).map(Designation::getName).orElse(""));
    row.add(changeType);
    row.add("");
    row.add("");
    return row.toArray();
  }

  private Map<String, String> composeVsTxt(Map<ValueSet, ValueSetCompareResult> res) {
    return res.entrySet().stream().map(es -> {
      StringBuilder sb = new StringBuilder();
      sb.append("\n\n");
      sb.append(TranslateUtil.translate("txt.valueset", i18n)).append(": ")
          .append(es.getKey().getTitle().getOrDefault(SessionStore.require().getLang(), es.getKey().getTitle().values().stream().findFirst().orElse("")))
          .append("\n");
      sb.append(TranslateUtil.translate("txt.url", i18n)).append(": ").append(es.getKey().getUri()).append("\n");

      sb.append(TranslateUtil.translate("txt.changes", i18n)).append(": ")
          .append(es.getKey().getFirstVersion().map(ValueSetVersionReference::getVersion).orElse(""))
          .append(" -> ")
          .append(es.getKey().getLastVersion().map(ValueSetVersionReference::getVersion).orElse(""))
          .append(" ");
      if (!es.getValue().getAdded().isEmpty()) {
        sb.append(TranslateUtil.translate("txt.added", i18n)).append(":").append(es.getValue().getAdded().size()).append("; ");
      }
      if (!es.getValue().getDeleted().isEmpty()) {
        sb.append(TranslateUtil.translate("txt.removed", i18n)).append(":").append(es.getValue().getDeleted().size()).append("; ");
      }
      String description = es.getKey().getLastVersion()
          .map(ValueSetVersion::getDescription)
          .map(d -> d.getOrDefault(SessionStore.require().getLang(), d.values().stream().findFirst().orElse(""))).orElse("");
      if (StringUtils.isNotEmpty(description)) {
        sb.append("\n").append(TranslateUtil.translate("txt.description", i18n)).append(": ").append(description);
      }
      return Pair.of(es.getKey().getId() + "vs", sb.toString());
    }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  private Map<String, String> composeResourceTxt(List<ReleaseResource> resources) {
    return resources.stream().filter(r -> !List.of("CodeSystem", "ValueSet").contains(r.getResourceType())).map(r -> {
      String sb = "\n\n" +
          TranslateUtil.translate("txt." + r.getResourceType(), i18n) + ": " +
          r.getResourceNames().getOrDefault(SessionStore.require().getLang(), r.getResourceNames().values().stream().findFirst().orElse("")) +
          "\n";
      return Pair.of(r.getResourceId() + r.getResourceType(), sb);
    }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  // ------------------------------ Common ------------------------------
  private static String getLatestProvenanceDate(List<Provenance> provenances) {
    return Optional.ofNullable(provenances).orElse(List.of()).stream()
        .max(Comparator.comparing(Provenance::getDate))
        .map(p -> p.getDate().toString())
        .orElse("");
  }
}
