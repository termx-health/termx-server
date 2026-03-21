package org.termx.terminology.fileimporter.codesystem;

import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import org.termx.terminology.ApiError;
import org.termx.terminology.fhir.FhirFshConverter;
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportProcessor;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystem;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystemVersion;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingProperty;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResponse;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult;
import org.termx.core.http.BinaryHttpClient;
import org.termx.terminology.terminology.codesystem.CodeSystemImportService;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.validator.CodeSystemValidationService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemImportAction;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityProperty;
import org.termx.ts.codesystem.EntityPropertyRule;
import org.termx.ts.codesystem.EntityPropertyValue;
import org.termx.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
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
import jakarta.inject.Singleton;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportMapper.CONCEPT_CODE;
import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportMapper.toAssociationTypes;
import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportMapper.toCodeSystem;
import static org.termx.ts.codesystem.EntityPropertyType.coding;
import static io.micronaut.core.util.CollectionUtils.last;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFileImportService {
  private final CodeSystemFileImportDryRunService dryRunService;
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

  @Transactional
  public CodeSystemFileImportResponse process(CodeSystemFileImportRequest request, byte[] file) {
    log.debug("=== IMPORT SERVICE DEBUG: process START ===");
    log.debug("IMPORT SERVICE DEBUG: CodeSystem ID: {}, Version: {}, Type: {}, File size: {} bytes", 
        request.getCodeSystem().getId(), request.getVersion().getNumber(), request.getType(), file != null ? file.length : 0);
    
    if ("json".equals(request.getType())) {
      log.debug("IMPORT SERVICE DEBUG: Processing JSON import");
      codeSystemFhirImportService.importCodeSystem(new String(file, StandardCharsets.UTF_8), request.getCodeSystem().getId());
      return new CodeSystemFileImportResponse();
    }
    if ("fsh".equals(request.getType())) {
      log.debug("IMPORT SERVICE DEBUG: Processing FSH import");
      String json = fhirFshConverter.orElseThrow(ApiError.TE806::toApiException).toFhir(new String(file, StandardCharsets.UTF_8)).join();
      codeSystemFhirImportService.importCodeSystem(json, request.getCodeSystem().getId());
      return new CodeSystemFileImportResponse();
    }
    
    log.debug("IMPORT SERVICE DEBUG: Processing file import (CSV/TSV/XLSX)");
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file);
    log.debug("IMPORT SERVICE DEBUG: File processing complete - Entities: {}, Properties: {}", 
        result.getEntities().size(), result.getProperties().size());
    
    log.debug("IMPORT SERVICE DEBUG: Saving import result...");
    CodeSystemFileImportResponse response = save(request, result);
    log.debug("IMPORT SERVICE DEBUG: Import complete - Errors: {}", response.getErrors().size());
    log.debug("=== IMPORT SERVICE DEBUG: process END ===");
    return response;
  }


  @Transactional
  public CodeSystemFileImportResponse save(CodeSystemFileImportRequest request, CodeSystemFileImportResult result) {
    log.debug("=== IMPORT SERVICE DEBUG: save START ===");
    log.debug("IMPORT SERVICE DEBUG: CodeSystem ID: {}, Version: {}, Entities: {}, Properties: {}", 
        request.getCodeSystem().getId(), request.getVersion().getNumber(), 
        result.getEntities().size(), result.getProperties().size());
    
    CodeSystemFileImportResponse resp = new CodeSystemFileImportResponse();
    FileProcessingCodeSystem reqCodeSystem = request.getCodeSystem();
    FileProcessingCodeSystemVersion reqVersion = request.getVersion();

    log.debug("IMPORT SERVICE DEBUG: Loading existing CodeSystem: {}", reqCodeSystem.getId());
    CodeSystem existingCodeSystem = codeSystemService.load(reqCodeSystem.getId(), true).orElse(null);
    log.debug("IMPORT SERVICE DEBUG: Existing CodeSystem: {}", existingCodeSystem != null ? "found" : "not found");
    
    log.debug("IMPORT SERVICE DEBUG: Loading existing CodeSystemVersion: {} / {}", reqCodeSystem.getId(), reqVersion.getNumber());
    CodeSystemVersion existingCodeSystemVersion = findVersion(reqCodeSystem.getId(), reqVersion.getNumber()).orElse(null);
    log.debug("IMPORT SERVICE DEBUG: Existing CodeSystemVersion: {}", existingCodeSystemVersion != null ? "found" : "not found");

    // Mapping
    log.debug("IMPORT SERVICE DEBUG: Mapping to CodeSystem...");
    var mappedCodeSystem = toCodeSystem(reqCodeSystem, reqVersion, result, existingCodeSystem, existingCodeSystemVersion);
    log.debug("IMPORT SERVICE DEBUG: CodeSystem mapped - Concepts: {}, Properties: {}", 
        mappedCodeSystem.getConcepts().size(), mappedCodeSystem.getProperties().size());
    
    var associationTypes = toAssociationTypes(result.getProperties());
    log.debug("IMPORT SERVICE DEBUG: Association types: {}", associationTypes);


    // Validation
    List<Issue> validationErrors = validate(request, mappedCodeSystem, existingCodeSystem).stream().filter(distinctByKey(Issue::formattedMessage)).toList();
    resp.getErrors().addAll(validationErrors);

    if (request.isDryRun()) {
      // Version comparison: shadow import + compare run in REQUIRES_NEW so rollback does not mark outer txn rollback-only
      log.info("Calculating the diff (validate data only, no persistent import)");
      log.info("\tCreating CS copy");
      CodeSystem copy = JsonUtil.fromJson(JsonUtil.toJson(mappedCodeSystem), CodeSystem.class);
      log.info("\tAdding _shadow suffix to version");
      copy.getVersions().forEach(cv -> cv.setId(null).setVersion(cv.getVersion() + "_shadow"));

      CodeSystemImportAction action = new CodeSystemImportAction()
          .setActivate(PublicationStatus.active.equals(request.getVersion().getStatus()))
          .setRetire(PublicationStatus.retired.equals(request.getVersion().getStatus()))
          .setGenerateValueSet(request.isGenerateValueSet())
          .setValueSetProperties(request.getValueSetProperties())
          .setCleanRun(request.isCleanVersion())
          .setCleanConceptRun(request.isReplaceConcept())
          .setSpaceToAdd(request.getSpace() != null && request.getSpacePackage() != null ?
              String.join("|", request.getSpace(), request.getSpacePackage()) : null);

      CodeSystemFileImportDryRunService.DryRunResult dryRunResult = dryRunService.dryRunImportCompareAndRollback(
          copy, associationTypes, action, reqCodeSystem.getId(), existingCodeSystemVersion);

      dryRunResult.importError().ifPresent(issue -> resp.getErrors().add(issue));
      if (dryRunResult.diff() != null) {
        resp.setDiff(dryRunResult.diff());
      }
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
        .setValueSetProperties(request.getValueSetProperties())
        .setCleanRun(request.isCleanVersion())
        .setCleanConceptRun(request.isReplaceConcept())
        .setSpaceToAdd(request.getSpace() != null && request.getSpacePackage() != null ?
            String.join("|", request.getSpace(), request.getSpacePackage()) : null);
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
                conceptService.query(base.setCodes(externalConceptCodes)).getData(),
                conceptService.query(base.setDesignationCiEq(codes).setCodes(null)).getData()
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
      mc.setDesignations(List.of());
      if (c.getVersions() != null) {
        mc.setDesignations(c.getVersions().stream().flatMap(v -> v.getDesignations() == null ? Stream.empty() : v.getDesignations().stream()).toList());
      }
      return mc;
    }

    public static MiniConcept fromConcept(ValueSetVersionConceptValue c) {
      MiniConcept mc = new MiniConcept();
      mc.setCode(c.getCode());
      mc.setCodeSystem(c.getCodeSystem());
      mc.setDesignations(List.of());
      return mc;
    }
  }
}
