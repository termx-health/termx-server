package org.termx.terminology.fhir;

import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import java.util.List;
import java.util.function.Function;

/**
 * Shared envelope for the FHIR {@code $batch-validate-code} operation (CodeSystem and ValueSet). The HL7
 * validator batches many code validations into a single request: a {@code Parameters} with one
 * {@code validation} part per code, each carrying that code's {@code $validate-code} input as a nested
 * {@code Parameters} resource, plus shared inputs (e.g. {@code tx-resource}, {@code displayLanguage}) at the
 * batch top level. The response mirrors it — one {@code validation} part per input, in order, each holding
 * the standard {@code $validate-code} result as a nested {@code Parameters}.
 */
public final class BatchValidateCodeSupport {
  private static final String VALIDATION = "validation";

  private BatchValidateCodeSupport() {
  }

  public static Parameters run(Parameters batch, Function<Parameters, Parameters> validateCode) {
    List<ParametersParameter> all = batch.getParameter() == null ? List.of() : batch.getParameter();
    List<ParametersParameter> shared = all.stream().filter(p -> !VALIDATION.equals(p.getName())).toList();

    Parameters resp = new Parameters();
    for (ParametersParameter item : all) {
      if (!VALIDATION.equals(item.getName())) {
        continue;
      }
      Parameters in = itemParameters(item);
      // Shared batch-level inputs (tx-resource, displayLanguage, …) apply to every validation; per-item
      // params come first so findParameter resolves them ahead of the shared defaults.
      shared.forEach(in::addParameter);

      Parameters result;
      try {
        result = validateCode.apply(in);
      } catch (FhirException e) {
        result = new Parameters()
            .addParameter(new ParametersParameter("result").setValueBoolean(false))
            .addParameter(new ParametersParameter("message").setValueString(e.getMessage()));
      }
      resp.addParameter(new ParametersParameter(VALIDATION).setResource(result));
    }
    return resp;
  }

  /** Each validation's input is a nested Parameters resource; tolerate the part-based shape as a fallback. */
  private static Parameters itemParameters(ParametersParameter item) {
    Parameters in = new Parameters();
    if (item.getResource() instanceof Parameters nested && nested.getParameter() != null) {
      nested.getParameter().forEach(in::addParameter);
    } else if (item.getPart() != null) {
      item.getPart().forEach(in::addParameter);
    }
    return in;
  }
}
