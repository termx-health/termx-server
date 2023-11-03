package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.core.treminology.Icd10ImportProvider;
import com.kodality.termx.core.treminology.LoincImportProvider;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.util.Base64;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemUploadExternalOperation implements TypeOperationDefinition {
  @Inject
  private LoincImportProvider loincImportProvider;
  @Inject
  private Icd10ImportProvider icd10ImportProvider;

  private static final String LOINC = "http://loinc.org";
  private static final String ICD10 = "http://hl7.org/fhir/sid/icd-10";
  private static final String ICD10_CM = "http://hl7.org/fhir/sid/icd-10-cm";

  @Override
  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  @Override
  public String getOperationName() {
    return "upload-external-code-system";
  }

  @Override
  public ResourceContent run(ResourceContent c) {
    Parameters req = FhirMapper.fromJson(c.getValue(), Parameters.class);

    String system = req.findParameter("system").map(ParametersParameter::getValueUri).orElse(null);
    Attachment file = req.findParameter("file").map(ParametersParameter::getValueAttachment).orElse(null);

    if (system == null) {
      throw new FhirException(400, IssueType.REQUIRED, "system parameter required");
    }
    if (file == null) {
      throw new FhirException(400, IssueType.REQUIRED, "file parameter required");
    }

    if (LOINC.equals(system)) {
      uploadLoinc(file);
    } else if (ICD10.equals(system)) {
      uploadICD10(file);
    } else if (ICD10_CM.equals(system)) {
      uploadICD10CM(file);
    } else {
      throw new FhirException(400, IssueType.NOTSUPPORTED, "provided system is not supported for terminology-upload");
    }
    return null;
  }

  private void uploadLoinc(Attachment attachment) {
    loincImportProvider.importLoinc(LOINC, Base64.getDecoder().decode(attachment.getData()));
  }

  private void uploadICD10(Attachment attachment) {
    icd10ImportProvider.importIcd10(ICD10, Base64.getDecoder().decode(attachment.getData()));
  }

  private void uploadICD10CM(Attachment attachment) {
    //TODO
    throw new FhirException(400, IssueType.NOTSUPPORTED, "icd-10-cm upload implementation is in process");
  }
}
