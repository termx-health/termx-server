package com.kodality.termx.editionint.loinc;

import com.kodality.termx.editionint.loinc.utils.LoincConcept;
import com.kodality.termx.editionint.loinc.utils.LoincConcept.LoincConceptAssociation;
import com.kodality.termx.editionint.loinc.utils.LoincConcept.LoincConceptProperty;
import com.kodality.termx.editionint.loinc.utils.LoincImportRequest;
import com.kodality.termx.editionint.loinc.utils.LoincMapper;
import com.kodality.termx.editionint.loinc.utils.answerlist.LoincAnswerList;
import com.kodality.termx.editionint.loinc.utils.answerlist.LoincAnswerListMapper;
import com.kodality.termx.editionint.loinc.utils.part.LoincPart;
import com.kodality.termx.editionint.loinc.utils.part.LoincPartMapper;
import com.kodality.termx.core.ts.CodeSystemImportProvider;
import com.kodality.termx.ts.Language;
import com.kodality.termx.core.ts.MapSetImportProvider;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoincService {
  @Inject
  private CodeSystemImportProvider csImportProvider;
  @Inject
  private MapSetImportProvider msImportProvider;

  @Transactional
  public void importLoinc(LoincImportRequest request, List<Pair<String, byte[]>> files) {
    processAnswerList(files, request);

    Map<String, LoincPart> parts = processParts(files);
    Map<String, LoincConcept> concepts = processTerminology(files);
    processLinguisticVariants(files, request, parts, concepts);
    csImportProvider.importCodeSystem(LoincPartMapper.toRequest(request, parts.values().stream().toList()));
    csImportProvider.importCodeSystem(LoincMapper.toRequest(request, concepts.values().stream().toList()));
  }

  private Map<String, LoincPart> processParts(List<Pair<String, byte[]>> files) {
    byte[] data = files.stream().filter(f -> f.getKey().equals("parts")).findFirst().map(Pair::getValue).orElse(null);

    RowListProcessor parser = csvProcessor(data);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();
    return rows.stream().map(r -> new LoincPart()
        .setCode(r[headers.indexOf("PartNumber")])
        .setDisplay(new HashMap<>(Map.of(Language.en, r[headers.indexOf("PartDisplayName")])))
        .setAlias(r[headers.indexOf("PartName")])
        .setType(r[headers.indexOf("PartTypeName")])).collect(Collectors.toMap(LoincPart::getCode, p -> p));
  }

  private Map<String, LoincConcept> processTerminology(List<Pair<String, byte[]>> files) {
    Map<String, LoincConcept> concepts = processConcepts(files);
    processAssociations(files, concepts);
    processAnswerListLink(files, concepts);
    processOrderObservation(files, concepts);
    return concepts;
  }

  private Map<String, LoincConcept> processConcepts(List<Pair<String, byte[]>> files) {
    byte[] terminology = files.stream().filter(f -> f.getKey().equals("terminology")).findFirst().map(Pair::getValue).orElse(null);
    RowListProcessor parser = csvProcessor(terminology);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    byte[] supplement = files.stream().filter(f -> f.getKey().equals("supplementary-properties")).findFirst().map(Pair::getValue).orElse(null);
    if (supplement != null) {
      parser = csvProcessor(supplement);
      rows.addAll(parser.getRows());
    }
    return rows.stream().collect(Collectors.groupingBy(r -> r[headers.indexOf("LoincNumber")])).entrySet().stream()
        .map(g -> new LoincConcept()
            .setCode(g.getKey())
            .setDisplay(new HashMap<>(Map.of(Language.en, g.getValue().stream().map(r -> r[headers.indexOf("LongCommonName")]).findFirst().orElse(""))))
            .setProperties(g.getValue().stream().map(r -> new LoincConceptProperty()
                .setName(r[headers.indexOf("PartTypeName")])
                .setType(EntityPropertyType.coding)
                .setValue(new Concept().setCode(r[headers.indexOf("PartNumber")]).setCodeSystem("loinc-part")))
                .collect(Collectors.toList())))
        .collect(Collectors.toMap(LoincConcept::getCode, c -> c));
  }

  private void processAnswerListLink(List<Pair<String, byte[]>> files, Map<String, LoincConcept> concepts) {
    byte[] answerListLink = files.stream().filter(f -> f.getKey().equals("answer-list-link")).findFirst().map(Pair::getValue).orElse(null);
    if (answerListLink == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(answerListLink);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    rows.forEach(r -> Optional.ofNullable(concepts.get(r[headers.indexOf("LoincNumber")])).ifPresent(c -> {
      c.setProperties(c.getProperties() == null ? new ArrayList<>() : c.getProperties());
      c.getProperties().add(new LoincConceptProperty()
          .setName("answer-list")
          .setValue(new Concept().setCode(r[headers.indexOf("AnswerListId")]).setCodeSystem("answer-list"))
          .setType(EntityPropertyType.coding));
      c.getProperties().add(new LoincConceptProperty()
              .setName("answer-list-binding")
              .setValue(r[headers.indexOf("AnswerListLinkType")])
              .setType(EntityPropertyType.string));
    }));
  }

  private void processOrderObservation(List<Pair<String, byte[]>> files, Map<String, LoincConcept> concepts) {
    byte[] orderObservation = files.stream().filter(f -> f.getKey().equals("order-observation")).findFirst().map(Pair::getValue).orElse(null);
    if (orderObservation == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(orderObservation);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    rows.forEach(r -> Optional.ofNullable(concepts.get(r[headers.indexOf("LOINC_NUM")])).ifPresent(c -> {
      c.setProperties(c.getProperties() == null ? new ArrayList<>() : c.getProperties());
      c.getProperties().add(new LoincConceptProperty()
          .setName("ORDER_OBS")
          .setValue(r[headers.indexOf("ORDER_OBS")])
          .setType(EntityPropertyType.string));
    }));
  }

  private void processLinguisticVariants(List<Pair<String,byte[]>> files, LoincImportRequest request, Map<String, LoincPart> parts, Map<String, LoincConcept> concepts) {
    byte[] translations = files.stream().filter(f -> f.getKey().equals("translations")).findFirst().map(Pair::getValue).orElse(null);
    String lang = request.getLanguage();
    if (translations == null || lang == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(translations);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();
    rows.forEach(r -> Optional.ofNullable(concepts.get(r[headers.indexOf("LOINC_NUM")])).ifPresent(c -> {
      updatePartDisplay(parts, c.getProperties(), "COMPONENT", Pair.of(lang, r[headers.indexOf("COMPONENT")]));
      updatePartDisplay(parts, c.getProperties(), "PROPERTY", Pair.of(lang, r[headers.indexOf("PROPERTY")]));
      updatePartDisplay(parts, c.getProperties(), "TIME", Pair.of(lang, r[headers.indexOf("TIME_ASPCT")]));
      updatePartDisplay(parts, c.getProperties(), "SYSTEM", Pair.of(lang, r[headers.indexOf("SYSTEM")]));
      updatePartDisplay(parts, c.getProperties(), "SCALE", Pair.of(lang, r[headers.indexOf("SCALE_TYP")]));
      updatePartDisplay(parts, c.getProperties(), "METHOD", Pair.of(lang, r[headers.indexOf("METHOD_TYP")]));
      updatePartDisplay(parts, c.getProperties(), "CLASS", Pair.of(lang, r[headers.indexOf("CLASS")]));

      c.getDisplay().put(lang, r[headers.indexOf("LONG_COMMON_NAME")]);

      c.setRelatedNames(c.getRelatedNames() == null ? new ArrayList<>() : c.getRelatedNames());
      c.getRelatedNames().add(Pair.of(lang, r[headers.indexOf("RELATEDNAMES2")]));
    }));
  }

  private void updatePartDisplay(Map<String, LoincPart> parts, List<LoincConceptProperty> properties, String propertyName, Pair<String, String> value) {
    if (StringUtils.isEmpty(value.getValue())) {
      return;
    }
    findPartCode(properties, propertyName)
        .flatMap(partCode -> Optional.ofNullable(parts.get(partCode)))
        .ifPresent(part -> part.getDisplay().put(value.getKey(), value.getValue()));
  }

  private Optional<String> findPartCode(List<LoincConceptProperty> properties, String propertyName) {
    return properties.stream()
        .filter(p -> p.getValue() instanceof Concept && p.getName().equals(propertyName))
        .findFirst()
        .map( p -> ((Concept) p.getValue()).getCode());
  }

  private void processAssociations(List<Pair<String, byte[]>> files, Map<String, LoincConcept> concepts) {
    byte[] panels = files.stream().filter(f -> f.getKey().equals("panels")).findFirst().map(Pair::getValue).orElse(null);
    if (panels == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(panels);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();
    rows.stream().collect(Collectors.groupingBy(r -> r[headers.indexOf("ParentLoinc")])).forEach((key, value) -> {
      Optional<LoincConcept> parent = Optional.ofNullable(concepts.get(key));
      if (parent.isPresent()) {
        parent.get().setAssociations(new ArrayList<>());
        value.forEach(v -> {
          String code = v[headers.indexOf("Loinc")];
          String order = v[headers.indexOf("SEQUENCE")];
          if (!parent.get().getCode().equals(code)) {
            parent.get().getAssociations().add(new LoincConceptAssociation().setTargetCode(code).setOrder(Integer.valueOf(order)));
          }
        });
      }
    });
  }

  private void processAnswerList(List<Pair<String, byte[]>> files, LoincImportRequest request) {
    byte[] answerList = files.stream().filter(f -> f.getKey().equals("answer-list")).findFirst().map(Pair::getValue).orElse(null);
    if (answerList == null) {
      return;
    }

    RowListProcessor parser = csvProcessor(answerList);
    List<String> headers = Arrays.asList(parser.getHeaders());
    List<String[]> rows = parser.getRows();

    List<LoincAnswerList> answers = rows.stream().filter(r -> StringUtils.isNotEmpty(r[headers.indexOf("AnswerStringId")]))
        .collect(Collectors.groupingBy(r -> r[headers.indexOf("AnswerStringId")])).values().stream().map(values -> {
          String[] r = values.get(0);
          return new LoincAnswerList()
              .setCode(r[headers.indexOf("AnswerStringId")])
              .setDisplay(r[headers.indexOf("DisplayText")])
              .setAnswerCode(r[headers.indexOf("LocalAnswerCode")])
              .setAnswerLists(values.stream().map(v -> Pair.of(v[headers.indexOf("AnswerListId")], Integer.valueOf(v[headers.indexOf("SequenceNumber")]))).toList());
        }).toList();
    List<LoincAnswerList> answerLists = rows.stream().collect(Collectors.groupingBy(r -> r[headers.indexOf("AnswerListId")])).values().stream().map(values -> {
      String[] r = values.get(0);
      return new LoincAnswerList()
          .setCode(r[headers.indexOf("AnswerListId")])
          .setDisplay(r[headers.indexOf("AnswerListName")])
          .setOid(r[headers.indexOf("AnswerListOID")]);
    }).toList();

    List<Pair<String, String>> mappings = rows.stream()
        .filter(r -> Optional.ofNullable(r[headers.indexOf("ExtCodeSystemVersion")]).orElse("").startsWith("http://snomed.info/sct/900000000000207008"))
        .map(r -> Pair.of(r[headers.indexOf("AnswerStringId")], r[headers.indexOf("ExtCodeId")]))
        .collect(Collectors.groupingBy(p -> p.getKey() + p.getValue()))
        .values().stream().map(v -> v.stream().findFirst().orElse(null)).filter(Objects::nonNull).toList();
    csImportProvider.importCodeSystem(LoincAnswerListMapper.toRequest(request, answers, answerLists));
    msImportProvider.importMapSet(LoincAnswerListMapper.toRequest(request, mappings));
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
}
