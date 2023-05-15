package com.kodality.termserver.fileimporter.codesystem;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.termserver.BinaryHttpClient;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termserver.fileimporter.codesystem.utils.FileAnalysisRequest;
import com.kodality.termserver.fileimporter.codesystem.utils.FileAnalysisResponse;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystem;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystemVersion;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingResponse;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessor;
import com.kodality.termserver.terminology.codesystem.CodeSystemImportService;
import com.kodality.termserver.terminology.codesystem.CodeSystemService;
import com.kodality.termserver.terminology.codesystem.CodeSystemValidationService;
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityProperty.EntityPropertyRule;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import io.micronaut.core.util.CollectionUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.transaction.annotation.Transactional;

import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.toAssociationTypes;
import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.toCodeSystem;
import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.toValueSet;
import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.toValueSetVersion;
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.coding;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFileImportService {
  private final CodeSystemFhirImportService codeSystemFhirImportService;
  private final CodeSystemImportService codeSystemImportService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemValidationService codeSystemValidationService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ConceptService conceptService;
  private final ValueSetService valueSetService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final ValueSetVersionService valueSetVersionService;

  private final BinaryHttpClient client = new BinaryHttpClient();

  public FileAnalysisResponse analyze(FileAnalysisRequest request) {
    byte[] file = loadFile(request.getLink());
    return analyze(request, file);
  }

  public void process(FileProcessingRequest request) {
    byte[] file = loadFile(request.getLink());
    process(request, file);
  }


  public FileAnalysisResponse analyze(FileAnalysisRequest request, byte[] file) {
    return FileProcessor.analyze(request.getType(), file);
  }

  public void process(FileProcessingRequest request, byte[] file) {
    if ("json".equals(request.getType())) {
      codeSystemFhirImportService.importCodeSystem(new String(file, StandardCharsets.UTF_8));
      return;
    }

    FileProcessingResponse result = FileProcessor.process(request.getType(), file, request.getProperties());
    saveProcessingResult(request, result);
  }

  @Transactional
  public void saveProcessingResult(FileProcessingRequest request, FileProcessingResponse result) {
    FileProcessingCodeSystem fpCodeSystem = request.getCodeSystem();
    FileProcessingCodeSystemVersion fpVersion = request.getVersion();
    CodeSystem existingCodeSystem = codeSystemService.load(fpCodeSystem.getId(), true).orElse(null);

    var mappedCodeSystem = toCodeSystem(fpCodeSystem, fpVersion, result, existingCodeSystem);
    var associationTypes = toAssociationTypes(result.getProperties());

    validate(request, existingCodeSystem, mappedCodeSystem);
    codeSystemImportService.importCodeSystem(mappedCodeSystem, associationTypes, fpVersion.getStatus().equals(PublicationStatus.active));

    if (fpVersion.getStatus().equals(PublicationStatus.retired)) {
      retireVersion(mappedCodeSystem);
    }
    if (request.isGenerateValueSet()) {
      generateValueSet(fpCodeSystem, fpVersion, mappedCodeSystem);
    }
  }

  private void validate(FileProcessingRequest request, CodeSystem existingCodeSystem, CodeSystem mappedCodeSystem) {
    List<Issue> issues = new ArrayList<>();
    if (existingCodeSystem != null) {
      issues.addAll(validateEntityPropertyValues(mappedCodeSystem, existingCodeSystem.getProperties()));
    }
    issues.addAll(codeSystemValidationService.validateConcepts(mappedCodeSystem.getConcepts(), mappedCodeSystem.getProperties()));

    List<Issue> unique = issues.stream().filter(distinctByKey(i -> StringSubstitutor.replace(i.getMessage(), i.getParams(), "{{", "}}"))).toList();
    if (unique.size() > 0 || request.isDryRun()) {
      throw new ApiException(400, unique);
    }
  }

  private void retireVersion(CodeSystem codeSystem) {
    String csId = codeSystem.getId();
    String latestVersion = codeSystem.getVersions().get(0).getVersion();
    codeSystemVersionService.retire(csId, latestVersion);
  }

  private void generateValueSet(FileProcessingCodeSystem fpCodeSystem, FileProcessingCodeSystemVersion fpVersion, CodeSystem codeSystem) {
    ValueSet existingValueSet = valueSetService.load(fpCodeSystem.getId()).orElse(null);
    ValueSet valueSet = toValueSet(codeSystem, existingValueSet);
    valueSetService.save(valueSet);

    ValueSetVersion valueSetVersion = toValueSetVersion(fpVersion, valueSet.getId(), codeSystem.getVersions().get(0));
    Optional<ValueSetVersion> existingVSVersion = valueSetVersionService.load(valueSet.getId(), valueSetVersion.getVersion());
    existingVSVersion.ifPresent(version -> valueSetVersion.setId(version.getId()));
    if (existingVSVersion.isPresent() && !existingVSVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", codeSystem.getVersions().get(0).getVersion()));
    }
    valueSetVersionService.save(valueSetVersion);
    valueSetVersionRuleService.save(valueSetVersion.getRuleSet().getRules(), valueSetVersion.getRuleSet().getId(), valueSet.getId());
  }


  // validation

  private List<Issue> validateEntityPropertyValues(CodeSystem codeSystem, List<EntityProperty> existingEntityProperties) {
    Map<String, EntityProperty> epMap = existingEntityProperties.stream().collect(toMap(EntityProperty::getName, Function.identity()));

    codeSystem.getProperties().stream().filter(p -> epMap.get(p.getName()) != null).forEach(p -> {
      p.setRequired(epMap.get(p.getName()).isRequired());
      p.setRule(epMap.get(p.getName()).getRule());
    });

    return validateExternalCodingPropertyValues(codeSystem, existingEntityProperties, epMap);
  }

  private List<Issue> validateExternalCodingPropertyValues(CodeSystem cs, List<EntityProperty> csProperties, Map<String, EntityProperty> csPropMap) {
    // all Coding property values
    List<EntityPropertyValue> conceptCodingPropertyValues = cs.getConcepts()
        .stream()
        .flatMap(c -> c.getVersions().stream())
        .flatMap(v -> v.getPropertyValues().stream())
        .filter(pv -> csPropMap.get(pv.getEntityProperty()) != null)
        .filter(pv -> coding.equals(csPropMap.get(pv.getEntityProperty()).getType()))
        .toList();

    // all concept codes, that Coding property values reference
    List<String> externalConceptCodes = conceptCodingPropertyValues
        .stream()
        .map(EntityPropertyValue::asCodingValue)
        .map(EntityPropertyValueCodingValue::getCode)
        .distinct()
        .toList();


    // entity prop name -> concepts[]
    Map<String, List<Concept>> concepts = csProperties.stream()
        .filter(ep -> coding.equals(ep.getType()))
        .collect(toMap(EntityProperty::getName, ep -> {
          EntityPropertyRule rule = ep.getRule();

          var base = new ConceptQueryParams();
          base.setLimit(10_000);
          if (StringUtils.isNotBlank(rule.getValueSet())) {
            base.setValueSet(rule.getValueSet());
          } else if (CollectionUtils.isNotEmpty(rule.getCodeSystems())) {
            base.setCodeSystem(StringUtils.join(rule.getCodeSystems(), ","));
          }

          return ListUtils.union(
              conceptService.query(base.setCode(StringUtils.join(externalConceptCodes, ","))).getData(),
              conceptService.query(base.setDesignationCiEq(StringUtils.join(externalConceptCodes, ",")).setCode(null)).getData()
          ).stream().filter(distinctByKey(Concept::getCode)).toList();
        }));

    return conceptCodingPropertyValues.stream().flatMap(pv -> {
      EntityProperty ep = csPropMap.get(pv.getEntityProperty());
      return validateExternalCodingPropertyValue(pv, ep, concepts).stream();
    }).toList();
  }

  private List<Issue> validateExternalCodingPropertyValue(EntityPropertyValue propertyValue, EntityProperty ep, Map<String, List<Concept>> epExternalConcepts) {
    List<Issue> errs = new ArrayList<>();

    // parse object value
    EntityPropertyValueCodingValue coding = propertyValue.asCodingValue();
    String codingCode = coding.getCode();
    log.info("Searching \"{}\"", codingCode);


    List<Concept> exactConcepts = findConceptByCode(epExternalConcepts.get(ep.getName()), codingCode);
    int matchedConcepts = exactConcepts.size();
    if (matchedConcepts == 1) {
      log.info("  Found exact concept!");
      propertyValue.setValue(new EntityPropertyValueCodingValue(exactConcepts.get(0).getCode(), exactConcepts.get(0).getCodeSystem()));
      return errs;
    } else {
      log.info("  Did not find exact concept. Fallback to designation search.");
    }


    List<Concept> designationConcepts = findConceptByDesignation(epExternalConcepts.get(ep.getName()), codingCode);
    int matchedDesignations = designationConcepts.size();
    if (matchedDesignations == 1) {
      log.info("  Found exact designation concept!");
      Concept desginationConcept = designationConcepts.get(0);
      propertyValue.setValue(new EntityPropertyValueCodingValue(desginationConcept.getCode(), desginationConcept.getCodeSystem()));
    } else if (matchedDesignations > 1) {
      log.info("  Too many designation candidates.");
      errs.add(error("Several concepts match the \"{{value}}\" value", Map.of("value", codingCode)));
    } else {
      log.info("Designation search failed");
      errs.add(error("Unknown reference to \"{{code}}\"", Map.of("code", codingCode)));
    }

    return errs;
  }

  private List<Concept> findConceptByCode(List<Concept> concepts, String codingCode) {
    return concepts.stream().filter(c -> c.getCode().equalsIgnoreCase(codingCode)).toList();
  }

  private List<Concept> findConceptByDesignation(List<Concept> designationConcepts, String codingText) {
    return designationConcepts.stream().filter(c -> {
      return c.getVersions().stream().flatMap(v -> v.getDesignations().stream()).anyMatch(d -> d.getName().equalsIgnoreCase(codingText));
    }).toList();
  }


  private Issue error(String message, Map<String, Object> params) {
    return Issue.error(message).setParams(params).setCode("CSFIS");
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  private byte[] loadFile(String link) {
    try {
      return client.GET(link).body();
    } catch (Exception e) {
      throw ApiError.TE711.toApiException();
    }
  }
}
