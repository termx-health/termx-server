package org.termx.terminology.fhir;

import org.termx.core.fhir.ConformanceInitializer.TermxGeneratedConformanceProvider;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import jakarta.inject.Singleton;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.Enumerations.CapabilityStatementKind;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.TerminologyCapabilities;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesClosureComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesCodeSystemVersionComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesExpansionComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesExpansionParameterComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesTranslationComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesValidateCodeComponent;
import io.micronaut.context.annotation.Value;

@Singleton
@RequiredArgsConstructor
public class TerminologyCapabilityInitializer implements TermxGeneratedConformanceProvider {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;

  @Value("${termx.api-url}")
  String apiUrl;

  @Override
  public Resource generate(String name) {
    if (name.equals("TerminologyCapabilities")) {
      return generateTerminologyCapabilities();
    }
    return null;
  }

  private TerminologyCapabilities generateTerminologyCapabilities() {
    TerminologyCapabilities tc = new TerminologyCapabilities();
    tc.setUrl(this.apiUrl + "/fhir/metadata");
    tc.setVersion("1");
    // name must be a token (computer-friendly, no whitespace) — the tx-ecosystem term-caps test asserts $token$.
    tc.setName("TermXTerminologyCapabilities");
    tc.setTitle("TermX Terminology Statement");
    tc.setStatus(PublicationStatus.ACTIVE);
    tc.setDate(new Date());
    tc.setKind(CapabilityStatementKind.INSTANCE);

    CodeSystemVersionQueryParams versionParams = new CodeSystemVersionQueryParams();
    versionParams.all();
    Map<String, List<CodeSystemVersion>> versions =
        codeSystemVersionService.query(versionParams).getData().stream().collect(Collectors.groupingBy(CodeSystemVersion::getCodeSystem));

    CodeSystemQueryParams p = new CodeSystemQueryParams();
    p.all();
    codeSystemService.query(p).getData().forEach(cs -> {
      tc.addCodeSystem()
          .setUri(cs.getUri())
          .setContent(CodeSystemContentMode.fromCode(cs.getContent()))
          .setVersion(!versions.containsKey(cs.getId()) ? List.of() :
              versions.get(cs.getId()).stream().map(v -> new TerminologyCapabilitiesCodeSystemVersionComponent()
                  .setCode(v.getVersion())
              ).toList()
          );
    });

    // The tx-ecosystem term-caps test asserts the $expand parameters the server supports. List every parameter the
    // expand operation honours (the names the suite expects, plus url/valueSetVersion which termx also accepts).
    TerminologyCapabilitiesExpansionComponent expansion = new TerminologyCapabilitiesExpansionComponent()
        .setHierarchical(true)
        .setPaging(false)
        .setIncomplete(false);
    for (String param : List.of(
        "activeOnly", "check-system-version", "count", "displayLanguage", "excludeNested", "force-system-version",
        "includeDefinition", "includeDesignations", "offset", "property", "system-version", "tx-resource",
        "url", "valueSetVersion")) {
      expansion.addParameter(new TerminologyCapabilitiesExpansionParameterComponent().setName(param));
    }
    tc.setExpansion(expansion);

    tc.setValidateCode(new TerminologyCapabilitiesValidateCodeComponent()
        .setTranslations(true)
    );
    tc.setTranslation(new TerminologyCapabilitiesTranslationComponent()
        .setNeedsMap(false)
    );
    tc.setClosure(new TerminologyCapabilitiesClosureComponent()
        .setTranslation(true)
    );

    return tc;
  }

}
