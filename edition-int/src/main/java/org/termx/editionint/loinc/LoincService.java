package org.termx.editionint.loinc;

import org.termx.core.sys.job.logger.ImportLog;
import org.termx.ts.codesystem.CodeSystemImportSummary;
import org.termx.editionint.loinc.utils.LoincConcept;
import org.termx.editionint.loinc.utils.LoincConcept.LoincConceptAssociation;
import org.termx.editionint.loinc.utils.LoincConcept.LoincConceptProperty;
import org.termx.editionint.loinc.utils.LoincImportRequest;
import org.termx.editionint.loinc.utils.LoincMapper;
import org.termx.editionint.loinc.utils.answerlist.LoincAnswerList;
import org.termx.editionint.loinc.utils.answerlist.LoincAnswerListMapper;
import org.termx.editionint.loinc.utils.part.LoincPart;
import org.termx.editionint.loinc.utils.part.LoincPartMapper;
import org.termx.core.ts.CodeSystemImportProvider;
import org.termx.ts.Language;
import org.termx.core.ts.MapSetImportProvider;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.EntityPropertyType;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives LOINC release ingestion: parses the CSVs unpacked by {@link
 * org.termx.editionint.loinc.utils.LoincZipReader} into in-memory part/concept models, then hands
 * the result to {@link CodeSystemImportProvider} for batch persistence. Called from
 * {@link LoincImportFromArchiveService} as the body of a background job.
 *
 * <h3>Performance notes</h3>
 * The previous implementation paid a hidden tax on every row: column lookups went through
 * {@code headers.indexOf("X")} inside the per-row stream lambdas, which is a linear scan of
 * the headers list. With ~2M rows in {@code LoincPartLink_*.csv} and 22 columns each, that
 * alone was ~10M linear scans for {@code processConcepts}. We now resolve each CSV's column
 * indices ONCE into a small {@code int} struct ({@link Idx}) and pass that to the loops, so
 * the per-row cost drops to one array dereference.
 *
 * <p>Other targeted cleanups: {@link #processAnswerList} folds three groupingBy passes into
 * one; {@link #processLinguisticVariants} builds a {@code partName → partCode} map per concept
 * once rather than re-scanning the property list seven times per translation row; and the
 * incoming {@code files} list is indexed by slot key up front so each phase doesn't re-walk
 * eight {@code Pair}s.
 *
 * <p>Each phase logs its wall-clock under the {@code loinc-import} prefix at INFO so admins
 * can see where time goes (parse vs. persist) when something looks slow.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoincService {
  @Inject
  private CodeSystemImportProvider csImportProvider;
  @Inject
  private MapSetImportProvider msImportProvider;

  // TODO(loinc-import): surface the DEEP-stage timings in the post-import email too.
  // ImportLog.successes currently captures everything LoincService logs at INFO under the
  // "loinc-import:" prefix, but the heavy lines from CodeSystemImportService and
  // CodeSystemEntityVersionService — "Concepts created (39 sec)", "Versions saved (58 sec)",
  // "Designations saved 'N' (62 sec)", "Properties saved 'N' (387 sec)", "Linkage created
  // (22 sec)", "Associations created (58 sec)" — are NOT captured because those classes
  // log directly to slf4j with no collector callback. To surface them, plumb a List<String>
  // (or a small LogCollector) through CodeSystemImportProvider.importCodeSystem →
  // TerminologyCodeSystemImportProvider → CodeSystemImportService.importCodeSystem →
  // CodeSystemEntityVersionService.batchUpsert. Each log.info(...) call there gets a
  // sibling collector.add(MessageFormatter.format(...).getMessage()) line, same pattern as
  // logAndCapture() below. Owner of the email then sees the full ~17-line timing breakdown
  // instead of the 9 LoincService-level lines we currently emit.
  @Transactional
  public ImportLog importLoinc(Map<String, Object> params) {
    long t0 = System.currentTimeMillis();
    LoincImportRequest request = (LoincImportRequest) params.get("request");
    List<Pair<String, byte[]>> files = (List<Pair<String, byte[]>>) params.get("files");
    // Index by slot once — every phase below used to do `files.stream().filter(...).findFirst()`.
    Map<String, byte[]> bySlot = toSlotMap(files);

    // Capture the per-phase wall-clock and per-CS summary lines so the post-import email
    // (ImportNotificationService) renders them under "Success Messages" instead of the
    // blank list it used to show. {@link #logAndCapture} writes the same string to slf4j
    // AND appends it to this list — single source of truth across logs + email.
    List<String> successLines = new ArrayList<>();

    CodeSystemImportSummary answerListSummary = processAnswerList(bySlot, request, successLines);
    if (answerListSummary != null) {
      successLines.add(formatSummary(answerListSummary));
    }

    long tParts = System.currentTimeMillis();
    Map<String, LoincPart> parts = processParts(bySlot);
    logAndCapture(successLines, "loinc-import: parsed {} parts in {} ms", parts.size(), System.currentTimeMillis() - tParts);

    long tTerm = System.currentTimeMillis();
    Map<String, LoincConcept> concepts = processTerminology(bySlot);
    logAndCapture(successLines,
        "loinc-import: parsed {} concepts (terminology + associations + answer-list-link + order-observation) in {} ms",
        concepts.size(), System.currentTimeMillis() - tTerm);

    long tLang = System.currentTimeMillis();
    processLinguisticVariants(bySlot, request, parts, concepts);
    logAndCapture(successLines, "loinc-import: applied linguistic variants in {} ms", System.currentTimeMillis() - tLang);

    long tSaveParts = System.currentTimeMillis();
    CodeSystemImportSummary partsSummary = csImportProvider.importCodeSystem(
        LoincPartMapper.toRequest(request, parts.values().stream().toList()));
    logAndCapture(successLines, "loinc-import: saved {} parts in {} ms", parts.size(), System.currentTimeMillis() - tSaveParts);
    if (partsSummary != null) {
      successLines.add(formatSummary(partsSummary));
    }

    long tSaveConcepts = System.currentTimeMillis();
    CodeSystemImportSummary conceptsSummary = csImportProvider.importCodeSystem(
        LoincMapper.toRequest(request, concepts.values().stream().toList()));
    logAndCapture(successLines, "loinc-import: saved {} concepts in {} ms", concepts.size(), System.currentTimeMillis() - tSaveConcepts);
    if (conceptsSummary != null) {
      successLines.add(formatSummary(conceptsSummary));
    }

    logAndCapture(successLines, "loinc-import: total {} ms", System.currentTimeMillis() - t0);
    return new ImportLog().setSuccesses(successLines);
  }

  /** Render a {@link CodeSystemImportSummary} as the human-readable line that lands in
   *  the import-completion email under "Success Messages":
   *  {@code "loinc@2.82: 109 325 concepts (109 325 added, 0 updated)"}. */
  private static String formatSummary(CodeSystemImportSummary s) {
    int total = s.getTotalConcepts() == null ? 0 : s.getTotalConcepts();
    int added = s.getAddedConcepts() == null ? 0 : s.getAddedConcepts();
    int updated = s.getUpdatedConcepts() == null ? 0 : s.getUpdatedConcepts();
    return String.format("%s@%s: %,d concepts (%,d added, %,d updated)",
        s.getCodeSystem(), s.getVersion(), total, added, updated);
  }

  /** Logs at INFO and also appends the formatted message to {@code capture} (so it ends up
   *  in {@link ImportLog#getSuccesses()} and ultimately in the post-import email). Mirrors
   *  the SLF4J {@code {}} placeholder semantics by reusing the parameterised formatter. */
  private void logAndCapture(List<String> capture, String pattern, Object... args) {
    log.info(pattern, args);
    capture.add(org.slf4j.helpers.MessageFormatter.arrayFormat(pattern, args).getMessage());
  }

  private Map<String, LoincPart> processParts(Map<String, byte[]> bySlot) {
    byte[] data = bySlot.get("parts");
    RowListProcessor parser = csvProcessor(data);
    Idx idx = Idx.of(parser.getHeaders(), "PartNumber", "PartDisplayName", "PartName", "PartTypeName");
    int iCode = idx.get("PartNumber");
    int iDisplay = idx.get("PartDisplayName");
    int iName = idx.get("PartName");
    int iType = idx.get("PartTypeName");

    List<String[]> rows = parser.getRows();
    Map<String, LoincPart> out = new LinkedHashMap<>(rows.size() * 2);
    for (String[] r : rows) {
      String code = r[iCode];
      out.put(code, new LoincPart()
          .setCode(code)
          .setDisplay(new HashMap<>(Map.of(Language.en, r[iDisplay])))
          .setAlias(r[iName])
          .setType(r[iType]));
    }
    return out;
  }

  private Map<String, LoincConcept> processTerminology(Map<String, byte[]> bySlot) {
    Map<String, LoincConcept> concepts = processConcepts(bySlot);
    processAssociations(bySlot, concepts);
    processAnswerListLink(bySlot, concepts);
    processOrderObservation(bySlot, concepts);
    return concepts;
  }

  private Map<String, LoincConcept> processConcepts(Map<String, byte[]> bySlot) {
    byte[] terminology = bySlot.get("terminology");
    RowListProcessor parser = csvProcessor(terminology);
    // Both terminology and supplementary CSVs ship with identical columns in LOINC 2.x; we
    // resolve the indices off the terminology parser and reuse them when appending the
    // supplementary rows below. If a future LOINC release ever ships different columns in
    // the supplementary file this will misread — guarded by the cheap header equality check.
    Idx idx = Idx.of(parser.getHeaders(), "LoincNumber", "LongCommonName", "PartNumber", "PartTypeName");
    int iLoinc = idx.get("LoincNumber");
    int iLongName = idx.get("LongCommonName");
    int iPart = idx.get("PartNumber");
    int iType = idx.get("PartTypeName");

    List<String[]> rows = parser.getRows();
    byte[] supplement = bySlot.get("supplementary-properties");
    if (supplement != null) {
      RowListProcessor suppParser = csvProcessor(supplement);
      // Defensive check — if column order ever drifts in supplementary, fail loudly rather
      // than silently misalign rows. Cheap (~one string-array equality compare).
      if (!java.util.Arrays.equals(parser.getHeaders(), suppParser.getHeaders())) {
        throw new IllegalStateException(
            "LoincPartLink_Supplementary.csv columns differ from LoincPartLink_Primary.csv — refusing to merge");
      }
      rows.addAll(suppParser.getRows());
    }

    // Single-pass: walk rows, group by LoincNumber while building LoincConcept objects.
    // The old code did rows.stream().collect(groupingBy) → entrySet().stream().map() —
    // two full passes and a throwaway intermediate Map<String, List<String[]>>.
    Map<String, LoincConcept> out = new LinkedHashMap<>(rows.size() / 8);
    for (String[] r : rows) {
      String code = r[iLoinc];
      LoincConcept c = out.get(code);
      if (c == null) {
        c = new LoincConcept()
            .setCode(code)
            .setDisplay(new HashMap<>(Map.of(Language.en, r[iLongName] == null ? "" : r[iLongName])))
            .setProperties(new ArrayList<>(8));
        out.put(code, c);
      }
      c.getProperties().add(new LoincConceptProperty()
          .setName(r[iType])
          .setType(EntityPropertyType.coding)
          .setValue(new Concept().setCode(r[iPart]).setCodeSystem("loinc-part")));
    }
    return out;
  }

  private void processAnswerListLink(Map<String, byte[]> bySlot, Map<String, LoincConcept> concepts) {
    byte[] answerListLink = bySlot.get("answer-list-link");
    if (answerListLink == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(answerListLink);
    Idx idx = Idx.of(parser.getHeaders(), "LoincNumber", "AnswerListId", "AnswerListLinkType");
    int iLoinc = idx.get("LoincNumber");
    int iListId = idx.get("AnswerListId");
    int iLinkType = idx.get("AnswerListLinkType");

    for (String[] r : parser.getRows()) {
      LoincConcept c = concepts.get(r[iLoinc]);
      if (c == null) {
        continue;
      }
      if (c.getProperties() == null) {
        c.setProperties(new ArrayList<>());
      }
      c.getProperties().add(new LoincConceptProperty()
          .setName("answer-list")
          .setValue(new Concept().setCode(r[iListId]).setCodeSystem("answer-list"))
          .setType(EntityPropertyType.coding));
      c.getProperties().add(new LoincConceptProperty()
          .setName("answer-list-binding")
          .setValue(r[iLinkType])
          .setType(EntityPropertyType.string));
    }
  }

  private void processOrderObservation(Map<String, byte[]> bySlot, Map<String, LoincConcept> concepts) {
    byte[] orderObservation = bySlot.get("order-observation");
    if (orderObservation == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(orderObservation);
    Idx idx = Idx.of(parser.getHeaders(), "LOINC_NUM", "ORDER_OBS");
    int iLoinc = idx.get("LOINC_NUM");
    int iOrder = idx.get("ORDER_OBS");

    for (String[] r : parser.getRows()) {
      LoincConcept c = concepts.get(r[iLoinc]);
      if (c == null) {
        continue;
      }
      if (c.getProperties() == null) {
        c.setProperties(new ArrayList<>());
      }
      c.getProperties().add(new LoincConceptProperty()
          .setName("ORDER_OBS")
          .setValue(r[iOrder])
          .setType(EntityPropertyType.string));
    }
  }

  private void processLinguisticVariants(Map<String, byte[]> bySlot, LoincImportRequest request, Map<String, LoincPart> parts, Map<String, LoincConcept> concepts) {
    byte[] translations = bySlot.get("translations");
    String lang = request.getLanguage();
    if (translations == null || lang == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(translations);
    Idx idx = Idx.of(parser.getHeaders(),
        "LOINC_NUM", "COMPONENT", "PROPERTY", "TIME_ASPCT", "SYSTEM", "SCALE_TYP",
        "METHOD_TYP", "CLASS", "LONG_COMMON_NAME", "RELATEDNAMES2");
    int iLoinc = idx.get("LOINC_NUM");
    int iComp = idx.get("COMPONENT");
    int iProp = idx.get("PROPERTY");
    int iTime = idx.get("TIME_ASPCT");
    int iSys = idx.get("SYSTEM");
    int iScale = idx.get("SCALE_TYP");
    int iMethod = idx.get("METHOD_TYP");
    int iClass = idx.get("CLASS");
    int iLongName = idx.get("LONG_COMMON_NAME");
    int iRelated = idx.get("RELATEDNAMES2");

    // Cache of concept-code → (partName → partCode). Built lazily on first encounter.
    // The old code called findPartCode() seven times per translation row, each a linear
    // scan over the concept's properties. With ~120k translation rows this was the single
    // biggest non-DB cost of the import.
    Map<String, Map<String, String>> partCodeByConceptCode = new HashMap<>(concepts.size() * 2);
    // Track which parts we've already updated for this language, so a part shared by many
    // concepts doesn't get its display map written 120k times with the same value.
    Set<String> appliedParts = new HashSet<>(parts.size() * 2);

    for (String[] r : parser.getRows()) {
      LoincConcept c = concepts.get(r[iLoinc]);
      if (c == null) {
        continue;
      }
      Map<String, String> partMap = partCodeByConceptCode.computeIfAbsent(c.getCode(), k -> buildPartCodeMap(c.getProperties()));

      applyPartDisplay(parts, appliedParts, partMap.get("COMPONENT"), lang, r[iComp]);
      applyPartDisplay(parts, appliedParts, partMap.get("PROPERTY"), lang, r[iProp]);
      applyPartDisplay(parts, appliedParts, partMap.get("TIME"), lang, r[iTime]);
      applyPartDisplay(parts, appliedParts, partMap.get("SYSTEM"), lang, r[iSys]);
      applyPartDisplay(parts, appliedParts, partMap.get("SCALE"), lang, r[iScale]);
      applyPartDisplay(parts, appliedParts, partMap.get("METHOD"), lang, r[iMethod]);
      applyPartDisplay(parts, appliedParts, partMap.get("CLASS"), lang, r[iClass]);

      c.getDisplay().put(lang, r[iLongName]);
      if (c.getRelatedNames() == null) {
        c.setRelatedNames(new ArrayList<>(1));
      }
      c.getRelatedNames().add(Pair.of(lang, r[iRelated]));
    }
  }

  /** Index a concept's coding-typed properties as partName → partCode for O(1) lookup. */
  private static Map<String, String> buildPartCodeMap(List<LoincConceptProperty> properties) {
    if (properties == null || properties.isEmpty()) {
      return Map.of();
    }
    Map<String, String> out = new HashMap<>(properties.size() * 2);
    for (LoincConceptProperty p : properties) {
      if (p.getValue() instanceof Concept con && p.getName() != null && !out.containsKey(p.getName())) {
        out.put(p.getName(), con.getCode());
      }
    }
    return out;
  }

  /** Apply a translated display string to a part if (a) the part exists and (b) we haven't
   *  already applied a translation to it for this language. The de-dupe matters because the
   *  same part code recurs across thousands of concepts; without it we'd HashMap.put() the
   *  same key→value pair tens of thousands of times. */
  private void applyPartDisplay(Map<String, LoincPart> parts, Set<String> appliedParts, String partCode, String lang, String value) {
    if (partCode == null || StringUtils.isEmpty(value)) {
      return;
    }
    LoincPart part = parts.get(partCode);
    if (part == null) {
      return;
    }
    String dedupeKey = partCode + '|' + lang;
    if (!appliedParts.add(dedupeKey)) {
      return;
    }
    part.getDisplay().put(lang, value);
  }

  private void processAssociations(Map<String, byte[]> bySlot, Map<String, LoincConcept> concepts) {
    byte[] panels = bySlot.get("panels");
    if (panels == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(panels);
    Idx idx = Idx.of(parser.getHeaders(), "ParentLoinc", "Loinc", "SEQUENCE");
    int iParent = idx.get("ParentLoinc");
    int iChild = idx.get("Loinc");
    int iSeq = idx.get("SEQUENCE");

    // Walk rows once, grouping by parent into the actual LoincConcept's associations list
    // rather than building an intermediate Map<String, List<String[]>>.
    for (String[] r : parser.getRows()) {
      String parentCode = r[iParent];
      LoincConcept parent = concepts.get(parentCode);
      if (parent == null) {
        continue;
      }
      String childCode = r[iChild];
      if (parentCode.equals(childCode)) {
        continue; // self-reference: skip
      }
      if (parent.getAssociations() == null) {
        parent.setAssociations(new ArrayList<>());
      }
      parent.getAssociations().add(new LoincConceptAssociation()
          .setTargetCode(childCode)
          .setOrder(Integer.valueOf(r[iSeq])));
    }
  }

  private CodeSystemImportSummary processAnswerList(Map<String, byte[]> bySlot, LoincImportRequest request, List<String> capture) {
    byte[] answerList = bySlot.get("answer-list");
    if (answerList == null) {
      return null;
    }

    long t0 = System.currentTimeMillis();
    RowListProcessor parser = csvProcessor(answerList);
    Idx idx = Idx.of(parser.getHeaders(),
        "AnswerStringId", "DisplayText", "LocalAnswerCode", "AnswerListId",
        "SequenceNumber", "AnswerListName", "AnswerListOID", "ExtCodeSystemVersion", "ExtCodeId");
    int iStringId = idx.get("AnswerStringId");
    int iDisplay = idx.get("DisplayText");
    int iLocal = idx.get("LocalAnswerCode");
    int iListId = idx.get("AnswerListId");
    int iSeq = idx.get("SequenceNumber");
    int iListName = idx.get("AnswerListName");
    int iListOid = idx.get("AnswerListOID");
    int iExtSys = idx.get("ExtCodeSystemVersion");
    int iExtCode = idx.get("ExtCodeId");

    // Single pass: collect (a) per-AnswerStringId aggregated entries, (b) per-AnswerListId
    // headers, and (c) SNOMED mappings — the previous version made three full passes plus
    // three groupingBy collectors over the same row list.
    Map<String, LoincAnswerList> answersById = new LinkedHashMap<>();
    Map<String, LoincAnswerList> listsById = new LinkedHashMap<>();
    Map<String, Pair<String, String>> mappings = new LinkedHashMap<>();

    for (String[] r : parser.getRows()) {
      String stringId = r[iStringId];
      String listId = r[iListId];

      // (a) answer entries, only when the row has a non-empty AnswerStringId.
      if (StringUtils.isNotEmpty(stringId)) {
        LoincAnswerList a = answersById.get(stringId);
        if (a == null) {
          a = new LoincAnswerList()
              .setCode(stringId)
              .setDisplay(r[iDisplay])
              .setAnswerCode(r[iLocal])
              .setAnswerLists(new ArrayList<>());
          answersById.put(stringId, a);
        }
        a.getAnswerLists().add(Pair.of(listId, Integer.valueOf(r[iSeq])));
      }

      // (b) answer-list headers — first-row-wins for each list id.
      if (!listsById.containsKey(listId)) {
        listsById.put(listId, new LoincAnswerList()
            .setCode(listId)
            .setDisplay(r[iListName])
            .setOid(r[iListOid]));
      }

      // (c) SNOMED-CT mappings — deduped by (stringId + extCode), first-seen wins to match
      // the previous behaviour.
      String extSys = r[iExtSys];
      if (extSys != null && extSys.startsWith("http://snomed.info/sct/900000000000207008")) {
        String extCode = r[iExtCode];
        String dedupe = stringId + extCode;
        mappings.putIfAbsent(dedupe, Pair.of(stringId, extCode));
      }
    }
    logAndCapture(capture, "loinc-import: parsed answer-list ({} answers, {} lists, {} snomed mappings) in {} ms",
        answersById.size(), listsById.size(), mappings.size(), System.currentTimeMillis() - t0);

    long tSave = System.currentTimeMillis();
    CodeSystemImportSummary summary = csImportProvider.importCodeSystem(LoincAnswerListMapper.toRequest(request,
        List.copyOf(answersById.values()), List.copyOf(listsById.values())));
    logAndCapture(capture, "loinc-import: saved answer-list code-system in {} ms", System.currentTimeMillis() - tSave);

    long tMap = System.currentTimeMillis();
    msImportProvider.importMapSet(LoincAnswerListMapper.toRequest(request,
        mappings.values().stream().filter(Objects::nonNull).toList()));
    logAndCapture(capture, "loinc-import: saved answer-list map-set ({} mappings) in {} ms",
        mappings.size(), System.currentTimeMillis() - tMap);

    return summary;
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

  /** Index the incoming files list by slot key. Callers used to do
   *  {@code files.stream().filter(...).findFirst()} per phase — eight phases × eight files
   *  was a small but very pointless O(N²) pass. */
  private static Map<String, byte[]> toSlotMap(List<Pair<String, byte[]>> files) {
    Map<String, byte[]> out = new HashMap<>(files.size() * 2);
    for (Pair<String, byte[]> f : files) {
      out.put(f.getKey(), f.getValue());
    }
    return out;
  }

  /**
   * Tiny holder that resolves a fixed set of column names to indices ONCE for a CSV. Replaces
   * the per-row {@code headers.indexOf("X")} pattern (a linear scan over the headers list
   * for every column read). With ~22 columns and millions of cell reads in {@code
   * LoincPartLink_*.csv}, this was easily the largest non-DB cost of the import.
   */
  private static final class Idx {
    private final Map<String, Integer> map;

    private Idx(Map<String, Integer> map) {
      this.map = map;
    }

    static Idx of(String[] headers, String... required) {
      Map<String, Integer> m = new HashMap<>(required.length * 2);
      for (String name : required) {
        int i = indexOf(headers, name);
        if (i < 0) {
          throw new IllegalArgumentException(
              "LOINC CSV is missing required column '" + name + "' (headers: " + java.util.Arrays.toString(headers) + ")");
        }
        m.put(name, i);
      }
      return new Idx(m);
    }

    int get(String name) {
      Integer i = map.get(name);
      if (i == null) {
        throw new IllegalStateException("Column '" + name + "' wasn't requested in Idx.of(...)");
      }
      return i;
    }

    private static int indexOf(String[] headers, String name) {
      for (int i = 0; i < headers.length; i++) {
        if (name.equals(headers[i])) {
          return i;
        }
      }
      return -1;
    }
  }
}
