package com.kodality.termx.observationdefinition;

import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.observationdefintion.ObservationDefinition;
import com.kodality.termx.observationdefintion.ObservationDefinition.ObservationDefinitionCategory;
import com.kodality.termx.observationdefintion.ObservationDefinitionImportRequest;
import com.kodality.termx.observationdefintion.ObservationDefinitionMapping;
import com.kodality.termx.observationdefintion.ObservationDefinitionMapping.ObservationDefinitionMappingTarget;
import com.kodality.termx.observationdefintion.ObservationDefinitionMember;
import com.kodality.termx.observationdefintion.ObservationDefinitionProtocol;
import com.kodality.termx.observationdefintion.ObservationDefinitionProtocol.ObservationDefinitionProtocolValue;
import com.kodality.termx.observationdefintion.ObservationDefinitionProtocol.ObservationDefinitionProtocolValueConcept;
import com.kodality.termx.observationdefintion.ObservationDefinitionProtocolUsage;
import com.kodality.termx.observationdefintion.ObservationDefinitionSearchParams;
import com.kodality.termx.observationdefintion.ObservationDefinitionStructure;
import com.kodality.termx.observationdefintion.ObservationDefinitionTimePrecision;
import com.kodality.termx.observationdefintion.ObservationDefinitionValue;
import com.kodality.termx.observationdefintion.ObservationDefinitionValue.ObservationDefinitionValueItem;
import com.kodality.termx.ts.CodeSystemProvider;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionImportService {
  private final CodeSystemProvider codeSystemProvider;
  private final ObservationDefinitionService observationDefinitionService;

  private static final String LOINC = "loinc";
  private static final String LOINC_ANSWER_LIST = "loinc-answer-list";
  private static final String LOINC_PART = "loinc-part";
  private static final String VERSION = "1.0.0";

  private static final String ANSWER_LIST = "answer-list";

  private static final String TIME = "TIME";
  private static final String PT = "LP6960-1";

  private static final String SCALE = "SCALE";
  private static final String NAR = "LP7749-7";
  private static final String DOC = "LP32888-7";
  private static final String QN = "LP7753-9";
  private static final String NOM = "LP7750-5";
  private static final String ORD = "LP7751-3";
  private static final String ORD_QN = "LP7752-1";
  private static final String SET = "LP7754-7";
  private static final String MULTI = "LP7748-9";
  private static final String ALL = "LP7746-3";
  private static final String NONE = "LP7747-1";

  private static final String CLASS = "CLASS";
  private static final String SYSTEM = "SYSTEM";
  private static final String PROPERTY = "PROPERTY";
  private static final String UNDEFINED = "LP6769-6";

  private static final String METHOD = "METHOD";


  @Transactional
  public List<ObservationDefinition> importDefinitions(ObservationDefinitionImportRequest request) {
    List<ObservationDefinition> observationDefinitions = new ArrayList<>();
    List<ObservationDefinition> existingDefinitions = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(request.getLoincCodes())) {

      ObservationDefinitionSearchParams obsDefParams = new ObservationDefinitionSearchParams();
      obsDefParams.setCodes(String.join(",", request.getLoincCodes()));
      obsDefParams.setLimit(request.getLoincCodes().size());
      List<ObservationDefinition> definitions = observationDefinitionService.search(obsDefParams).getData();
      existingDefinitions.addAll(definitions);

      List<String> loincCodes = request.getLoincCodes().stream().filter(c -> definitions.stream().noneMatch(d -> d.getCode().equals(c))).toList();

      ConceptQueryParams params = new ConceptQueryParams();
      params.setCodeSystem(LOINC);
      params.setCode(String.join(",", loincCodes));
      params.setLimit(loincCodes.size());
      List<Concept> concepts = codeSystemProvider.searchConcepts(params).getData();
      observationDefinitions.addAll(concepts.stream().map(this::fromLoincToObservationDefinition).toList());
    }

    log.info("Saving " + observationDefinitions.size() + " observation definition(s)");
    observationDefinitions.forEach(observationDefinitionService::save);
    observationDefinitions.forEach(obs -> obs.setMappings(getMappings(obs)));
    observationDefinitions.forEach(observationDefinitionService::save);
    observationDefinitions.addAll(existingDefinitions);
    return observationDefinitions;
  }

  private ObservationDefinition fromLoincToObservationDefinition(Concept concept) {
    long start = System.currentTimeMillis();
    log.info(concept.getCode()  + " observation definition mapping started");

    ObservationDefinition observationDefinition = new ObservationDefinition();
    observationDefinition.setCode(concept.getCode());
    observationDefinition.setPublisher("loinc");
    observationDefinition.setUrl("http://loinc.org/" + concept.getCode());
    observationDefinition.setStatus(PublicationStatus.draft);
    observationDefinition.setVersion(VERSION);

    CodeSystemEntityVersion version = concept.getVersions().stream().filter(v -> !v.getStatus().equals(PublicationStatus.retired)).toList().get(concept.getVersions().size() - 1);
    observationDefinition.setNames(getNames(version, "display"));
    observationDefinition.setAlias(getNames(version, "alias"));
    observationDefinition.setKeywords(getNames(version, "key-words"));
    observationDefinition.setTimePrecision(getTimePrecision(version));
    observationDefinition.setStructure(getStructure(version));
    observationDefinition.setValue(getValue(version));
    observationDefinition.setMembers(getMembers(version));
    observationDefinition.setCategory(getCategory(version));
    observationDefinition.setProtocol(getProtocol(version));

    log.info(concept.getCode()  + " observation definition mapping finished ({} sec)", (System.currentTimeMillis() - start) / 1000);
    return observationDefinition;
  }

  private LocalizedName getNames(CodeSystemEntityVersion version, String type) {
    return new LocalizedName(version.getDesignations().stream()
        .filter(d -> d.getDesignationType().equals(type))
        .collect(Collectors.toMap(Designation::getLanguage, Designation::getName)));
  }

  private String getTimePrecision(CodeSystemEntityVersion version) {
    boolean timestamp = version.getPropertyValues().stream()
        .anyMatch(pv -> pv.getEntityProperty().equals(TIME) && JsonUtil.fromJson(JsonUtil.toJson(pv.getValue()), Concept.class).getCode().equals(PT));
    return timestamp ? ObservationDefinitionTimePrecision.timestamp : ObservationDefinitionTimePrecision.period;
  }

  private List<String> getStructure(CodeSystemEntityVersion version) {
    boolean subsumes = version.getAssociations() != null && version.getAssociations().stream().anyMatch(a -> a.getAssociationType().equals("subsumes"));
    if (subsumes) {
      return List.of(ObservationDefinitionStructure.panel);
    } else {
      Optional<EntityPropertyValue> scale = version.getPropertyValues().stream().filter(pv -> pv.getEntityProperty().equals(SCALE)).findFirst();
      return scale.map(s -> JsonUtil.fromJson(JsonUtil.toJson(s.getValue()), Concept.class).getCode()).map(this::getStructure).orElse(List.of());
    }
  }

  private List<String> getStructure(String code) {
    if (List.of(NAR, DOC, ALL, QN, NOM, ORD, ORD_QN).contains(code)) {
      return List.of(ObservationDefinitionStructure.value);
    }
    if (List.of(SET, MULTI, NONE).contains(code)) {
//      return List.of(ObservationDefinitionStructure.component);
      return List.of(ObservationDefinitionStructure.value);
    }
    return List.of();
  }

  private ObservationDefinitionValue getValue(CodeSystemEntityVersion version) {
    boolean subsumes = version.getAssociations() != null && version.getAssociations().stream().anyMatch(a -> a.getAssociationType().equals("subsumes"));
    if (subsumes) {
      return null;
    }

    Optional<EntityPropertyValue> scale = version.getPropertyValues().stream().filter(pv -> pv.getEntityProperty().equals(SCALE)).findFirst();
    ObservationDefinitionValue value = scale.map(s -> JsonUtil.fromJson(JsonUtil.toJson(s.getValue()), Concept.class).getCode()).map(this::getValue).orElse(null);
    if (value != null) {
      value.setValues(getAnswerListValues(version));
    }
    return value;
  }

  private ObservationDefinitionValue getValue(String code) {
    if (List.of(NAR, DOC, ALL).contains(code)) {
      return new ObservationDefinitionValue().setType("string").setBehaviour("editable").setUsage("values");
    }
    if (List.of(QN, ORD_QN).contains(code)) {
      return new ObservationDefinitionValue().setType("Quantity").setBehaviour("editable").setUsage("values");
    }
    if (List.of(NOM, ORD).contains(code)) {
      return new ObservationDefinitionValue().setType("CodeableConcept").setBehaviour("editable").setUsage("values");
    }
    return null;
  }

  private List<ObservationDefinitionValueItem> getAnswerListValues(CodeSystemEntityVersion version) {
    Optional<EntityPropertyValue> answerList = version.getPropertyValues().stream().filter(pv -> pv.getEntityProperty().equals(ANSWER_LIST)).findFirst();
    if (answerList.isEmpty()) {
      return List.of();
    }
    ConceptQueryParams params = new ConceptQueryParams();
    params.setCodeSystem(LOINC_ANSWER_LIST);
    params.setAssociationSource("is-a|" + JsonUtil.fromJson(JsonUtil.toJson(answerList.get().getValue()), Concept.class).getCode());
    params.setLimit(-1);
    QueryResult<Concept> answers = codeSystemProvider.searchConcepts(params);
    return answers.getData().stream().map(c -> new ObservationDefinitionValueItem().setCode(c.getCode()).setCodeSystem(c.getCodeSystem())).toList();
  }

  private List<ObservationDefinitionMember> getMembers(CodeSystemEntityVersion version) {
    boolean subsumes = version.getAssociations() != null && version.getAssociations().stream().anyMatch(a -> a.getAssociationType().equals("subsumes"));
    if (!subsumes) {
      return List.of();
    }
    Map<String, List<CodeSystemAssociation>> associations = version.getAssociations().stream().collect(Collectors.groupingBy(CodeSystemAssociation::getTargetCode));
    ObservationDefinitionImportRequest request = new ObservationDefinitionImportRequest().setLoincCodes(associations.keySet().stream().toList());
    List<ObservationDefinition> definitions = importDefinitions(request);
    return definitions.stream().flatMap(d -> associations.get(d.getCode()).stream().map(a -> getMember(d, a.getOrderNumber()))).toList();
  }

  private ObservationDefinitionMember getMember(ObservationDefinition definition, int order) {
    ObservationDefinitionMember member = new ObservationDefinitionMember();
    member.setItem(definition);
    member.setOrderNumber(order);
    member.setNames(definition.getNames());
    return member;
  }

  private List<ObservationDefinitionCategory> getCategory(CodeSystemEntityVersion version) {
    return version.getPropertyValues().stream()
        .filter(pv -> List.of(CLASS, SYSTEM, PROPERTY).contains(pv.getEntityProperty()))
        .map(pv -> JsonUtil.fromJson(JsonUtil.toJson(pv.getValue()), Concept.class).getCode())
        .filter(code -> !code.equals(UNDEFINED))
        .map(code -> new ObservationDefinitionCategory().setCode(code).setCodeSystem(LOINC_PART)).toList();
  }

  private ObservationDefinitionProtocol getProtocol(CodeSystemEntityVersion version) {
    return version.getPropertyValues().stream()
        .filter(pv -> pv.getEntityProperty().equals(METHOD))
        .map(pv -> JsonUtil.fromJson(JsonUtil.toJson(pv.getValue()), Concept.class).getCode()).findFirst()
        .map(method -> new ObservationDefinitionProtocol().setMethod(new ObservationDefinitionProtocolValue()
            .setUsage(ObservationDefinitionProtocolUsage.values)
            .setValues(List.of(new ObservationDefinitionProtocolValueConcept().setCode(method).setCodeSystem(LOINC_PART)))
        )).orElse(new ObservationDefinitionProtocol());
  }

  private List<ObservationDefinitionMapping> getMappings(ObservationDefinition obs) {
    ObservationDefinitionMapping mapping = new ObservationDefinitionMapping();
    mapping.setConcept(obs.getCode());
    mapping.setCodeSystem(LOINC);
    mapping.setTarget(new ObservationDefinitionMappingTarget().setId(obs.getId()).setType("observation-definition"));
    mapping.setRelation("equivalent");
    mapping.setOrderNumber(1);
    return List.of(mapping);
  }
}
