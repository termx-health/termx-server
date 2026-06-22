package org.termx.terminology.fhir.valueset.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ResourceType;
import org.termx.terminology.fhir.BatchValidateCodeSupport;

/**
 * FHIR ValueSet {@code $batch-validate-code}: validates many codes against value sets in one request, delegating
 * each {@code validation} item to {@link ValueSetValidateCodeOperation} (so inline tx-resource value sets,
 * designations, displays etc. behave identically to the single-code operation). The HL7 validator posts the batch
 * to {@code POST /ValueSet/$batch-validate-code}.
 */
@Singleton
@RequiredArgsConstructor
public class ValueSetBatchValidateCodeOperation implements TypeOperationDefinition {
  private final ValueSetValidateCodeOperation validateCodeOperation;

  public String getResourceType() {
    return ResourceType.ValueSet.name();
  }

  public String getOperationName() {
    return "batch-validate-code";
  }

  public ResourceContent run(ResourceContent p) {
    Parameters batch = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = BatchValidateCodeSupport.run(batch, validateCodeOperation::run);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }
}
