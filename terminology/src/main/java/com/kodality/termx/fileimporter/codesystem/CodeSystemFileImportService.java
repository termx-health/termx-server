package com.kodality.termx.fileimporter.codesystem;

import com.kodality.commons.db.transaction.TransactionManager;
import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.ApiError;
import com.kodality.termx.fhir.FhirFshConverter;
import com.kodality.termx.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportProcessor;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportRequest;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystem;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystemVersion;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingProperty;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportResponse;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportResult;
import com.kodality.termx.http.BinaryHttpClient;
import com.kodality.termx.terminology.codesystem.CodeSystemImportService;
import com.kodality.termx.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.codesystem.validator.CodeSystemValidationService;
import com.kodality.termx.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.terminology.codesystem.compare.CodeSystemCompareResult;
import com.kodality.termx.terminology.codesystem.compare.CodeSystemCompareResult.CodeSystemCompareResultDiffItem;
import com.kodality.termx.terminology.codesystem.compare.CodeSystemCompareService;
import com.kodality.termx.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemImportAction;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyRule;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
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
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.transaction.annotation.Transactional;

import static com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportMapper.CONCEPT_CODE;
import static com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportMapper.toAssociationTypes;
import static com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportMapper.toCodeSystem;
import static com.kodality.termx.ts.codesystem.EntityPropertyType.coding;
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
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final Optional<FhirFshConverter> fhirFshConverter;

  private final BinaryHttpClient client = new BinaryHttpClient();


  public CodeSystemFileImportResponse process(CodeSystemFileImportRequest request) {
    byte[] file = loadFile(request.getLink());
    return process(request, file);
  }

  public CodeSystemFileImportResponse process(CodeSystemFileImportRequest request, byte[] file) {
    if ("json".equals(request.getType())) {
      codeSystemFhirImportService.importCodeSystem(new String(file, StandardCharsets.UTF_8), request.getCodeSystem().getId());
      return new CodeSystemFileImportResponse();
    }
    if ("fsh".equals(request.getType())) {
      String json = fhirFshConverter.orElseThrow(ApiError.TE806::toApiException).toFhir(new String(file, StandardCharsets.UTF_8)).join();
      codeSystemFhirImportService.importCodeSystem(json, request.getCodeSystem().getId());
      return new CodeSystemFileImportResponse();
    }
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request.getType(), file, request.getProperties());
    return save(request, result);
  }


  @Transactional
  public CodeSystemFileImportResponse save(CodeSystemFileImportRequest request, CodeSystemFileImportResult result) {
    CodeSystemFileImportResponse resp = new CodeSystemFileImportResponse();
    FileProcessingCodeSystem reqCodeSystem = request.getCodeSystem();
    FileProcessingCodeSystemVersion reqVersion = request.getVersion();

    log.info("Trying to load existing CodeSystem");
    CodeSystem existingCodeSystem = codeSystemService.load(reqCodeSystem.getId(), true).orElse(null);
    log.info("Trying to load existing CodeSystemVersion");
    CodeSystemVersion existingCodeSystemVersion = findVersion(reqCodeSystem.getId(), reqVersion.getNumber()).orElse(null);


    // Mapping
    log.info("Mapping to CodeSystem");
    var mappedCodeSystem = toCodeSystem(reqCodeSystem, reqVersion, result, existingCodeSystem, existingCodeSystemVersion);
    var associationTypes = toAssociationTypes(result.getProperties());

    if (request.isCleanVersion() && existingCodeSystem != null) {
      log.info("Trying to clean CodeSystem version data");
      cleanRun(existingCodeSystem, reqVersion.getNumber());
    }

    // Validation
    List<Issue> validationErrors = validate(request, mappedCodeSystem, existingCodeSystem).stream().filter(distinctByKey(Issue::formattedMessage)).toList();
    resp.getErrors().addAll(validationErrors);

    if (request.isDryRun()) {
      // Version comparison
      log.info("Calculating the diff");
      log.info("\tCreating CS copy");
      CodeSystem copy = JsonUtil.fromJson(JsonUtil.toJson(mappedCodeSystem), CodeSystem.class);
      log.info("\tAdding _shadow suffix to version");
      copy.getVersions().forEach(cv -> cv.setId(null).setVersion(cv.getVersion() + "_shadow"));

      try {
        log.info("\tImporting CS copy");
        CodeSystemImportAction action = new CodeSystemImportAction()
            .setActivate(PublicationStatus.active.equals(request.getVersion().getStatus()))
            .setRetire(PublicationStatus.retired.equals(request.getVersion().getStatus()))
            .setGenerateValueSet(request.isGenerateValueSet())
            .setCleanRun(request.isCleanVersion())
            .setCleanConceptRun(request.isReplaceConcept());
        codeSystemImportService.importCodeSystem(copy, associationTypes, action);
      } catch (Exception e) {
        TransactionManager.rollback();
        resp.getErrors().add(ApiError.TE716.toIssue(Map.of("exception", ExceptionUtils.getMessage(e))));
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
    CodeSystemImportAction action = new CodeSystemImportAction()
        .setActivate(PublicationStatus.active.equals(request.getVersion().getStatus()))
        .setRetire(PublicationStatus.retired.equals(request.getVersion().getStatus()))
        .setGenerateValueSet(request.isGenerateValueSet())
        .setCleanRun(request.isCleanVersion())
        .setCleanConceptRun(request.isReplaceConcept());
    codeSystemImportService.importCodeSystem(mappedCodeSystem, associationTypes, action);
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

  // validation

  private List<Issue> validate(CodeSystemFileImportRequest request, CodeSystem mappedCodeSystem, CodeSystem existingCodeSystem) {
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

      issues.add(ApiError.TE713.toIssue(Map.of("prop", k, "ranges", ranges)));
    });

    return issues;
  }

  private List<Issue> validateExternalCodingPropertyValue(EntityPropertyValue propertyValue, EntityProperty ep, Map<String, List<MiniConcept>> epExternalConcepts) {
    List<Issue> errs = new ArrayList<>();

    // parse object value
    EntityPropertyValueCodingValue coding = propertyValue.asCodingValue();
    String codingCode = coding.getCode();
    log.debug("Searching \"{}\"", codingCode);


    List<MiniConcept> exactConcepts = findConceptByCode(epExternalConcepts.get(ep.getName()), codingCode);
    int matchedConcepts = exactConcepts.size();
    if (matchedConcepts == 1) {
      log.debug("\tfound exact concept!");
      propertyValue.setValue(new EntityPropertyValueCodingValue(exactConcepts.get(0).getCode(), exactConcepts.get(0).getCodeSystem()));
      return errs;
    } else {
      log.debug("\tdid not find exact concept. Fallback to designation search.");
    }


    List<MiniConcept> designationConcepts = findConceptByDesignation(epExternalConcepts.get(ep.getName()), codingCode);
    int matchedDesignations = designationConcepts.size();
    if (matchedDesignations == 1) {
      log.debug("\tfound exact designation concept!");
      MiniConcept desginationConcept = designationConcepts.get(0);
      propertyValue.setValue(new EntityPropertyValueCodingValue(desginationConcept.getCode(), desginationConcept.getCodeSystem()));
    } else if (matchedDesignations > 1) {
      log.debug("\ttoo many designation candidates.");
      errs.add(ApiError.TE714.toIssue(Map.of("value", codingCode)));
    } else {
      log.debug("\tdesignation search failed");
      errs.add(ApiError.TE715.toIssue(Map.of("code", codingCode, "codeSystem", ruleString(ep.getRule()))));
    }

    return errs;
  }

  // validation helpers

  private void prepareEntityProperties(CodeSystem codeSystem, List<EntityProperty> existingEntityProperties) {
    // entity property names
    List<String> propNames = codeSystem.getProperties().stream().map(EntityProperty::getName).toList();
    // list of existing properties that are used in mapped CS
    List<EntityProperty> selectedProps = existingEntityProperties == null ? List.of() :
        existingEntityProperties.stream().filter(v -> propNames.contains(v.getName())).toList();
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
    Map<String, List<MiniConcept>> concepts = csPropMap.values().stream()
        .filter(ep -> coding.equals(ep.getType()))
        .collect(toMap(EntityProperty::getName, ep -> {
          EntityPropertyRule rule = ep.getRule();
          String codes = StringUtils.join(externalConceptCodes, ",");

          log.info("Searching concept(s) for property \"{}\"", ep.getName());
          long start = System.currentTimeMillis();

          if (StringUtils.isNotBlank(rule.getValueSet())) {
            List<ValueSetVersionConcept> expand = valueSetVersionConceptService.expand(rule.getValueSet(), null);
            log.info("\texpand took {} millis", System.currentTimeMillis() - start);
            return expand.stream().map(MiniConcept::fromConcept).toList();
          }

          if (CollectionUtils.isNotEmpty(rule.getCodeSystems())) {
            var base = new ConceptQueryParams();
            base.setLimit(10_000);
            base.setCodeSystem(StringUtils.join(rule.getCodeSystems(), ","));
            List<Concept> resp = ListUtils.union(
                conceptService.query(base.setCode(codes)).getData(),
                conceptService.query(base.setDesignationCiEq(codes).setCode(null)).getData()
            ).stream().filter(distinctByKey(Concept::getCode)).toList();

            log.info("\tquery took {} millis", System.currentTimeMillis() - start);
            return resp.stream().map(MiniConcept::fromConcept).toList();
          }

          return List.of();
        }));


    return conceptCodingPropertyValues.stream().flatMap(pv -> {
      EntityProperty ep = csPropMap.get(pv.getEntityProperty());
      return validateExternalCodingPropertyValue(pv, ep, concepts).stream();
    }).toList();
  }

  private List<MiniConcept> findConceptByCode(List<MiniConcept> concepts, String codingCode) {
    return concepts.stream().filter(c -> c.getCode().equalsIgnoreCase(codingCode)).toList();
  }

  private List<MiniConcept> findConceptByDesignation(List<MiniConcept> designationConcepts, String codingText) {
    return designationConcepts.stream().filter(c -> {
      return c.getDesignations().stream().anyMatch(d -> d.getName().equalsIgnoreCase(codingText));
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

  @Data
  @Accessors(chain = true)
  private static class MiniConcept {
    private String code;
    private String codeSystem;
    private List<Designation> designations;

    public static MiniConcept fromConcept(ValueSetVersionConcept vc) {
      MiniConcept c = MiniConcept.fromConcept(vc.getConcept());
      if (vc.getDisplay() != null) {
        c.setDesignations(new ArrayList<>(List.of(vc.getDisplay())));
      }
      if (vc.getAdditionalDesignations() != null) {
        c.setDesignations(ListUtils.union(c.getDesignations(), vc.getAdditionalDesignations().stream().toList()));
      }
      return c;
    }

    public static MiniConcept fromConcept(Concept c) {
      MiniConcept mc = new MiniConcept();
      mc.setCode(c.getCode());
      mc.setCodeSystem(c.getCodeSystem());
      if (c.getVersions() != null) {
        mc.setDesignations(c.getVersions().stream().flatMap(v -> v.getDesignations() == null ? Stream.empty() : v.getDesignations().stream()).toList());
      }
      return mc;
    }

    public static MiniConcept fromConcept(ValueSetVersionConceptValue c) {
      MiniConcept mc = new MiniConcept();
      mc.setCode(c.getCode());
      mc.setCodeSystem(c.getCodeSystem());
      return mc;
    }
  }
}
