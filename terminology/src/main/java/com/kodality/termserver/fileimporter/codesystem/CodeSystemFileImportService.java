package com.kodality.termserver.fileimporter.codesystem;

import com.kodality.commons.db.transaction.TransactionManager;
import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termserver.BinaryHttpClient;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termserver.fileimporter.codesystem.utils.FileAnalysisRequest;
import com.kodality.termserver.fileimporter.codesystem.utils.FileAnalysisResponse;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystem;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystemVersion;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingProperty;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingResponse;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingResult;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessor;
import com.kodality.termserver.terminology.codesystem.CodeSystemImportService;
import com.kodality.termserver.terminology.codesystem.CodeSystemService;
import com.kodality.termserver.terminology.codesystem.CodeSystemValidationService;
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termserver.terminology.codesystem.compare.CodeSystemCompareResult;
import com.kodality.termserver.terminology.codesystem.compare.CodeSystemCompareResult.CodeSystemCompareResultDiffItem;
import com.kodality.termserver.terminology.codesystem.compare.CodeSystemCompareService;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityProperty.EntityPropertyRule;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.transaction.annotation.Transactional;

import static com.kodality.commons.model.Issue.error;
import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.CONCEPT_CODE;
import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.toAssociationTypes;
import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.toCodeSystem;
import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.toValueSet;
import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper.toValueSetVersion;
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.coding;
import static io.micronaut.core.util.CollectionUtils.isNotEmpty;
import static io.micronaut.core.util.CollectionUtils.last;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFileImportService {
  private final CodeSystemCompareService codeSystemCompareService;
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

  public FileAnalysisResponse analyze(FileAnalysisRequest request, byte[] file) {
    return FileProcessor.analyze(request.getType(), file);
  }


  public FileProcessingResponse process(FileProcessingRequest request) {
    byte[] file = loadFile(request.getLink());
    return process(request, file);
  }

  public FileProcessingResponse process(FileProcessingRequest request, byte[] file) {
    if ("json".equals(request.getType())) {
      codeSystemFhirImportService.importCodeSystem(new String(file, StandardCharsets.UTF_8));
      return new FileProcessingResponse();
    }

    FileProcessingResult result = FileProcessor.process(request.getType(), file, request.getProperties());
    return save(request, result);
  }


  @Transactional
  public FileProcessingResponse save(FileProcessingRequest request, FileProcessingResult result) {
    FileProcessingResponse resp = new FileProcessingResponse();
    FileProcessingCodeSystem reqCodeSystem = request.getCodeSystem();
    FileProcessingCodeSystemVersion reqVersion = request.getVersion();

    log.info("Trying to load existing CodeSystem");
    CodeSystem existingCodeSystem = codeSystemService.load(reqCodeSystem.getId(), true).orElse(null);
    log.info("Trying to load existing CodeSystemVersion");
    CodeSystemVersion existingCodeSystemVersion = findVersion(reqCodeSystem.getId(), reqVersion.getVersion()).orElse(null);


    // Mapping
    log.info("Mapping to CodeSystem");
    var mappedCodeSystem = toCodeSystem(reqCodeSystem, reqVersion, result, existingCodeSystem, existingCodeSystemVersion);
    var mappedCsVersionStatus = mappedCodeSystem.getVersions().get(0).getStatus();
    var associationTypes = toAssociationTypes(result.getProperties());

    if (request.isCleanRun() && existingCodeSystem != null) {
      log.info("Trying to clean CodeSystem version data");
      cleanRun(existingCodeSystem, reqVersion.getVersion());
    }

    if (request.isDryRun()) {
      // Validation
      List<Issue> validationErrors =
          validate(request, mappedCodeSystem, existingCodeSystem).stream().filter(distinctByKey(Issue::formattedMessage)).toList();
      resp.setErrors(validationErrors);


      // Version comparison
      log.info("Calculating the diff");
      log.info("\tCreating CS copy");
      CodeSystem copy = JsonUtil.fromJson(JsonUtil.toJson(mappedCodeSystem), CodeSystem.class);
      log.info("\tAdding _shadow suffix to version");
      copy.getVersions().forEach(cv -> cv.setId(null).setVersion(cv.getVersion() + "_shadow"));

      try {
        log.info("\tImporting CS copy");
        codeSystemImportService.importCodeSystem(copy, associationTypes, PublicationStatus.active.equals(mappedCsVersionStatus));
      } catch (Exception e) {
        TransactionManager.rollback();
        resp.getErrors().add(error(ExceptionUtils.getMessage(e)));
        return resp;
      }


      Long sourceVersionId = null;
      if (existingCodeSystemVersion != null) {
        log.info("\tUsing the source version '{}' with ID '{}'", existingCodeSystemVersion.getVersion(), existingCodeSystemVersion.getId());
        sourceVersionId = existingCodeSystemVersion.getId();
      } else {
        log.info("\tSource version is not set");
      }

      log.info("\tFinding the target version by version code '{}'", copy.getVersions().get(0).getVersion());
      Long targetVersionId = findVersion(reqCodeSystem.getId(), copy.getVersions().get(0).getVersion()).map(CodeSystemVersion::getId).orElseThrow();

      log.info("\tComparing two versions: '{}' and '{}'", sourceVersionId, targetVersionId);
      CodeSystemCompareResult compare = codeSystemCompareService.compare(sourceVersionId, targetVersionId);
      resp.setDiff(composeCompareSummary(compare));

      log.info("\tCancelling the _shadow versions");
      copy.getVersions().forEach(cv -> codeSystemVersionService.cancel(cv.getId(), cv.getCodeSystem()));

      TransactionManager.rollback();
      return resp;
    }


    // Actual import

    // NB: 'importCodeSystem' cancels the persisted version and saves a new one.
    // If the new version has an ID, the importer will simply update the cancelled one.
    // Setting null to prevent that.
    mappedCodeSystem.getVersions().forEach(cv -> cv.setId(null));
    codeSystemImportService.importCodeSystem(mappedCodeSystem, associationTypes, PublicationStatus.active.equals(mappedCsVersionStatus));

    if (PublicationStatus.retired.equals(mappedCsVersionStatus)) {
      retireVersion(mappedCodeSystem);
    }
    if (request.isGenerateValueSet()) {
      generateValueSet(reqCodeSystem, reqVersion, mappedCodeSystem);
    }
    return resp;
  }

  private Optional<CodeSystemVersion> findVersion(String csId, String version) {
    CodeSystemVersionQueryParams params = new CodeSystemVersionQueryParams();
    params.setVersion(version);
    params.setCodeSystem(csId);
    params.setLimit(1);
    return codeSystemVersionService.query(params).findFirst();
  }

  private void cleanRun(CodeSystem cs, String versionCode) {
    if (cs.getVersions().size() > 1 || cs.getVersions().size() == 1 && !PublicationStatus.draft.equals(cs.getVersions().get(0).getStatus())) {
      throw new ApiClientException("Clean run is only available for code systems with one version in the status 'draft'.");
    }

    CodeSystemVersion version = findVersion(cs.getId(), versionCode).orElseThrow();
    codeSystemVersionService.cancel(version.getId(), version.getCodeSystem());
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

  private List<Issue> validate(FileProcessingRequest request, CodeSystem mappedCodeSystem, CodeSystem existingCodeSystem) {
    List<Issue> issues = new ArrayList<>();
    if (existingCodeSystem != null) {
      prepareEntityProperties(mappedCodeSystem, existingCodeSystem.getProperties());
    }
    Map<String, EntityProperty> epMap = mappedCodeSystem.getProperties().stream().collect(toMap(EntityProperty::getName, Function.identity()));
    log.info("Validating required properties");
    issues.addAll(validateRequiredProperties(request.getProperties(), mappedCodeSystem.getConcepts(), epMap));
    log.info("Decorating Coding entity properties");
    issues.addAll(decorateExternalCodingProperties(mappedCodeSystem, epMap));
    log.info("Validating mapped concepts");
    issues.addAll(codeSystemValidationService.validateConcepts(mappedCodeSystem.getConcepts(), mappedCodeSystem.getProperties()));
    return issues;
  }


  private List<Issue> validateRequiredProperties(List<FileProcessingProperty> csProperties, List<Concept> concepts, Map<String, EntityProperty> epMap) {
    Map<String, List<Integer>> reqs = new HashMap<>();
    List<Issue> issues = new ArrayList<>();

    for (FileProcessingProperty p : csProperties) {
      List<Integer> propIssues = reqs.computeIfAbsent(p.getName(), s -> new ArrayList<>());
      IntStream.range(0, concepts.size()).forEach(idx -> {
        int row = idx + 2; // assumes CSV/TSV header is on the 1st row
        Concept c = concepts.get(idx);

        if (CONCEPT_CODE.equals(p.getName()) && c.getCode() == null) {
          propIssues.add(row);
        }

        if (epMap.get(p.getName()) != null && epMap.get(p.getName()).isRequired()) {
          boolean designationExists =
              c.getVersions().stream().flatMap(v -> v.getDesignations().stream()).anyMatch(d -> p.getName().equals(d.getDesignationType()));
          boolean propertyValueExists =
              c.getVersions().stream().flatMap(v -> v.getPropertyValues().stream()).anyMatch(pv -> p.getName().equals(pv.getEntityProperty()));

          if (!designationExists && !propertyValueExists) {
            propIssues.add(row);
          }
        }
      });
    }

    reqs.keySet().stream().filter(k -> !reqs.get(k).isEmpty()).forEach(k -> {
      String ranges = getRanges(reqs.get(k)).stream().map(range -> {
        Integer min = Collections.min(range);
        Integer max = Collections.max(range);
        return min.equals(max) ? min.toString() : "%s-%s".formatted(min, max);
      }).collect(Collectors.joining(", "));

      issues.add(error("Property \"{{prop}}\" is missing value on row(s): {{ranges}}", Map.of("prop", k, "ranges", ranges)));
    });

    return issues;
  }

  private List<Issue> validateExternalCodingPropertyValue(EntityPropertyValue propertyValue, EntityProperty ep, Map<String, List<Concept>> epExternalConcepts) {
    List<Issue> errs = new ArrayList<>();

    // parse object value
    EntityPropertyValueCodingValue coding = propertyValue.asCodingValue();
    String codingCode = coding.getCode();
    log.debug("Searching \"{}\"", codingCode);


    List<Concept> exactConcepts = findConceptByCode(epExternalConcepts.get(ep.getName()), codingCode);
    int matchedConcepts = exactConcepts.size();
    if (matchedConcepts == 1) {
      log.debug("\tfound exact concept!");
      propertyValue.setValue(new EntityPropertyValueCodingValue(exactConcepts.get(0).getCode(), exactConcepts.get(0).getCodeSystem()));
      return errs;
    } else {
      log.debug("\tdid not find exact concept. Fallback to designation search.");
    }


    List<Concept> designationConcepts = findConceptByDesignation(epExternalConcepts.get(ep.getName()), codingCode);
    int matchedDesignations = designationConcepts.size();
    if (matchedDesignations == 1) {
      log.debug("\tfound exact designation concept!");
      Concept desginationConcept = designationConcepts.get(0);
      propertyValue.setValue(new EntityPropertyValueCodingValue(desginationConcept.getCode(), desginationConcept.getCodeSystem()));
    } else if (matchedDesignations > 1) {
      log.debug("\ttoo many designation candidates.");
      errs.add(error("Several concepts match the \"{{value}}\" value", Map.of("value", codingCode)));
    } else {
      log.debug("\tdesignation search failed");
      errs.add(error("Unknown reference \"{{code}}\" to \"{{codeSystem}}\"", Map.of("code", codingCode, "codeSystem", ruleString(ep.getRule()))));
    }

    return errs;
  }

  // validation helpers

  private List<Concept> findConceptByCode(List<Concept> concepts, String codingCode) {
    return concepts.stream().filter(c -> c.getCode().equalsIgnoreCase(codingCode)).toList();
  }

  private List<Concept> findConceptByDesignation(List<Concept> designationConcepts, String codingText) {
    return designationConcepts.stream().filter(c -> {
      return c.getVersions().stream().flatMap(v -> v.getDesignations().stream()).anyMatch(d -> d.getName().equalsIgnoreCase(codingText));
    }).toList();
  }

  private void prepareEntityProperties(CodeSystem codeSystem, List<EntityProperty> existingEntityProperties) {
    // entity property names
    List<String> propNames = codeSystem.getProperties().stream().map(EntityProperty::getName).toList();
    // list of existing properties that are used in mapped CS
    List<EntityProperty> selectedProps = existingEntityProperties.stream().filter(v -> propNames.contains(v.getName())).toList();
    // handy map
    Map<String, EntityProperty> propMap = selectedProps.stream().collect(toMap(EntityProperty::getName, Function.identity()));

    log.info("Decorating entity properties: {}", selectedProps.stream().map(EntityProperty::getName).toList());
    codeSystem.getProperties().stream()
        .filter(p -> propMap.get(p.getName()) != null)
        .forEach(p -> {
          p.setRequired(propMap.get(p.getName()).isRequired());
          p.setRule(propMap.get(p.getName()).getRule());
        });
  }

  private List<Issue> decorateExternalCodingProperties(CodeSystem cs, Map<String, EntityProperty> csPropMap) {
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
    Map<String, List<Concept>> concepts = csPropMap.values().stream()
        .filter(ep -> coding.equals(ep.getType()))
        .collect(toMap(EntityProperty::getName, ep -> {
          EntityPropertyRule rule = ep.getRule();
          String codes = StringUtils.join(externalConceptCodes, ",");

          var base = new ConceptQueryParams();
          base.setLimit(10_000);
          if (StringUtils.isNotBlank(rule.getValueSet())) {
            base.setValueSet(rule.getValueSet());
          } else if (isNotEmpty(rule.getCodeSystems())) {
            base.setCodeSystem(StringUtils.join(rule.getCodeSystems(), ","));
          }

          long start = System.currentTimeMillis();
          log.info("Searching concept(s) for property \"{}\"", ep.getName());
          List<Concept> resp = ListUtils.union(
              conceptService.query(base.setCode(codes)).getData(),
              conceptService.query(base.setDesignationCiEq(codes).setCode(null)).getData()
          ).stream().filter(distinctByKey(Concept::getCode)).toList();
          log.info("\ttook {} millis", System.currentTimeMillis() - start);
          return resp;
        }));


    return conceptCodingPropertyValues.stream().flatMap(pv -> {
      EntityProperty ep = csPropMap.get(pv.getEntityProperty());
      return validateExternalCodingPropertyValue(pv, ep, concepts).stream();
    }).toList();
  }

  private String composeCompareSummary(CodeSystemCompareResult res) {
    List<String> msg = new ArrayList<>();
    msg.add("##### Created ######");
    res.getAdded().forEach(d -> msg.add(" * %s".formatted(d)));

    msg.add("##### Changed ######");
    res.getChanged().forEach(c -> {
      CodeSystemCompareResultDiffItem old = c.getDiff().getOld();
      CodeSystemCompareResultDiffItem mew = c.getDiff().getMew();

      List<String> changes = new ArrayList<>();
      changes.add(compareDiffElements("status", old.getStatus(), mew.getStatus()));
      changes.add(compareDiffElements("description", old.getDescription(), mew.getDescription()));
      changes.add(compareDiffElements(old.getDesignations(), mew.getDesignations()));
      changes.add(compareDiffElements(old.getProperties(), mew.getProperties()));

      if (changes.stream().anyMatch(StringUtils::isNotBlank)) {
        msg.add(" * %s".formatted(c.getCode()));
        changes.stream().filter(StringUtils::isNotBlank).forEach(msg::add);
      }
    });


    msg.add("##### Deleted ######");
    res.getDeleted().forEach(d -> msg.add(" * %s".formatted(d)));

    return StringUtils.join(msg, "\n");
  }

  private String compareDiffElements(String key, String el1, String el2) {
    return !Objects.equals(el1, el2) ? "\t- %s: \"%s\" -> \"%s\"".formatted(key, el1, el2) : null;
  }

  private String compareDiffElements(List<String> els1, List<String> els2) {
    Map<String, List<String>> collect1 = els1 == null ? Map.of() : els1.stream()
        .map(PipeUtil::parsePipe)
        .collect(groupingBy(s -> s[0], Collectors.mapping(s -> s[1], Collectors.toList())));
    Map<String, List<String>> collect2 = els2 == null ? Map.of() : els2.stream()
        .map(PipeUtil::parsePipe)
        .collect(groupingBy(s -> s[0], Collectors.mapping(s -> s[1], Collectors.toList())));

    return SetUtils.union(collect1.keySet(), collect2.keySet())
        .stream()
        .map(k -> {
          List<String> v1 = collect1.getOrDefault(k, List.of());
          List<String> v2 = collect2.getOrDefault(k, List.of());
          return ListUtils.isEqualList(v1, v2) ? null : "\t- %s: \"%s\" -> \"%s\"".formatted(k, StringUtils.join(v1, ", "), StringUtils.join(v2, ", "));
        })
        .filter(Objects::nonNull)
        .collect(Collectors.joining("\n"));
  }


  // utils

  private byte[] loadFile(String link) {
    try {
      return client.GET(link).body();
    } catch (Exception e) {
      throw ApiError.TE711.toApiException();
    }
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  private String ruleString(EntityPropertyRule rule) {
    return rule.getCodeSystems() != null ? StringUtils.join(rule.getCodeSystems(), ", ") : rule.getValueSet();
  }

  private List<List<Integer>> getRanges(List<Integer> integers) {
    List<List<Integer>> els = new ArrayList<>();
    integers.stream().sorted().forEach(el -> {
      if (els.isEmpty() || el - 1 > last(last(els))) {
        els.add(new ArrayList<>(List.of(el)));
      } else {
        last(els).add(el);
      }
    });
    return els;
  }
}
