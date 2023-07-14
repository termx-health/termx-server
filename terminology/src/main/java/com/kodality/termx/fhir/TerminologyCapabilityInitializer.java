package com.kodality.termx.fhir;

import com.kodality.termx.fhir.ConformanceInitializer.TermxGeneratedConformanceProvider;
import com.kodality.termx.terminology.codesystem.CodeSystemService;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import jakarta.inject.Singleton;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.Enumerations.CapabilityStatementKind;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.TerminologyCapabilities;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesClosureComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesExpansionComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesExpansionParameterComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesTranslationComponent;
import org.hl7.fhir.r5.model.TerminologyCapabilities.TerminologyCapabilitiesValidateCodeComponent;

@Singleton
@RequiredArgsConstructor
public class TerminologyCapabilityInitializer implements TermxGeneratedConformanceProvider {
  private final CodeSystemService codeSystemService;

  @Override
  public Resource generate(String name) {
    if (name.equals("TerminologyCapabilities")) {
      return generateTerminologyCapabilities();
    }
    return null;
  }

  private TerminologyCapabilities generateTerminologyCapabilities() {
    TerminologyCapabilities tc = new TerminologyCapabilities();
    tc.setUrl("https://termx.kodality.dev/api/fhir/metadata");
    tc.setVersion("1");
    tc.setName("TermX Terminology Statement");
    tc.setTitle("TermX Terminology Statement");
    tc.setStatus(PublicationStatus.ACTIVE);
    tc.setDate(new Date());
    tc.setKind(CapabilityStatementKind.INSTANCE);

    CodeSystemQueryParams p = new CodeSystemQueryParams();
    p.all();
    codeSystemService.query(p).getData().forEach(cs -> {
      tc.addCodeSystem()
          .setUri(cs.getUri())
          .setContent(CodeSystemContentMode.fromCode(cs.getContent()));
    });

    tc.setExpansion(new TerminologyCapabilitiesExpansionComponent()
        .setHierarchical(true)
        .setPaging(false)
        .setIncomplete(false)
        .addParameter(new TerminologyCapabilitiesExpansionParameterComponent().setName("url"))
        .addParameter(new TerminologyCapabilitiesExpansionParameterComponent().setName("valueSetVersion"))
        .addParameter(new TerminologyCapabilitiesExpansionParameterComponent().setName("excludeNested"))
        .addParameter(new TerminologyCapabilitiesExpansionParameterComponent().setName("activeOnly"))
    );

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
