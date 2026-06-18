package org.termx.terminology.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import io.micronaut.context.annotation.Factory;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ResourceType;
import org.termx.terminology.fhir.BatchValidateCodeSupport;

/**
 * FHIR CodeSystem {@code $batch-validate-code}: validates many codes against code systems in one request,
 * delegating each {@code validation} item to {@link CodeSystemValidateCodeOperation} (so inline tx-resource
 * code systems, displays, versions etc. behave identically to the single-code operation).
 */
@Factory
@RequiredArgsConstructor
public class CodeSystemBatchValidateCodeOperation implements TypeOperationDefinition {
  private final CodeSystemValidateCodeOperation validateCodeOperation;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
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
