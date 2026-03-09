package org.termx.ucum.ts;

import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class UcumSupplementDesignationService {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";

  private final CodeSystemService codeSystemService;
  private final ConceptService conceptService;

  public void enrich(List<ValueSetVersionConcept> concepts, String preferredLanguage) {
    if (concepts == null || concepts.isEmpty()) {
      return;
    }
    List<ValueSetVersionConcept> ucumConcepts = concepts.stream()
        .filter(c -> c != null && c.getConcept() != null && StringUtils.isNotEmpty(c.getConcept().getCode()))
        .filter(c -> UCUM.equals(c.getConcept().getCodeSystem()) || UCUM_URI.equals(c.getConcept().getCodeSystemUri()))
        .toList();
    if (ucumConcepts.isEmpty()) {
      return;
    }

    List<CodeSystem> supplements = codeSystemService.query(new CodeSystemQueryParams()
        .setContent(CodeSystemContent.supplement)
        .all()).getData().stream()
        .filter(cs -> UCUM.equals(cs.getBaseCodeSystem()) || UCUM_URI.equals(cs.getBaseCodeSystemUri()))
        .toList();
    if (supplements.isEmpty()) {
      return;
    }

    List<String> codes = ucumConcepts.stream().map(c -> c.getConcept().getCode()).distinct().toList();
    Map<String, List<Designation>> supplementDesignations = supplements.stream()
        .map(CodeSystem::getId)
        .filter(StringUtils::isNotEmpty)
        .flatMap(cs -> conceptService.query(new ConceptQueryParams().setCodeSystem(cs).setCodes(codes).all()).getData().stream())
        .filter(c -> c.getVersions() != null && !c.getVersions().isEmpty())
        .flatMap(c -> c.getVersions().stream().flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream()
            .filter(d -> languageMatches(d.getLanguage(), preferredLanguage))
            .map(d -> Map.entry(c.getCode(), d))))
        .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    ucumConcepts.forEach(c -> {
      List<Designation> merged = new ArrayList<>(Optional.ofNullable(c.getAdditionalDesignations()).orElse(List.of()));
      merged.addAll(supplementDesignations.getOrDefault(c.getConcept().getCode(), List.of()));
      c.setAdditionalDesignations(distinctDesignations(merged));
    });
  }

  private static boolean languageMatches(String language, String preferredLanguage) {
    return StringUtils.isEmpty(preferredLanguage) || language != null &&
        (language.equals(preferredLanguage) || language.startsWith(preferredLanguage + "-"));
  }

  private static List<Designation> distinctDesignations(List<Designation> designations) {
    return designations.stream().collect(Collectors.collectingAndThen(
        Collectors.toMap(d -> String.join("|",
                org.apache.commons.lang3.StringUtils.defaultString(d.getDesignationType()),
                org.apache.commons.lang3.StringUtils.defaultString(d.getLanguage()),
                org.apache.commons.lang3.StringUtils.defaultString(d.getName())),
            d -> d, (a, b) -> a, LinkedHashMap::new),
        m -> new ArrayList<>(m.values())));
  }
}
