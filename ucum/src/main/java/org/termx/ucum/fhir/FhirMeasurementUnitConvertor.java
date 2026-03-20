package org.termx.ucum.fhir;

import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fhir.ucum.UcumException;
import org.termx.ucum.dto.ConvertResponseDto;
import org.termx.ucum.service.UcumService;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class FhirMeasurementUnitConvertor {

  private final UcumService ucumService;

  public Parameters convert(@NotNull BigDecimal value, String source, String target) {
    Parameters parameters = new Parameters();
    BigDecimal converted;
    try {
      ConvertResponseDto response = ucumService.convert(value, source, target);
      converted = new BigDecimal(response.getResult());
    } catch (UcumException | RuntimeException e) {
      log.error("Failed to convert {} from {} to {}", value, source, target, e);
      parameters.setParameter(List.of(new ParametersParameter().setName("result").setValueBoolean(false)));
      return parameters;
    }
    ParametersParameter result = new ParametersParameter().setName("result").setValueBoolean(true);
    ParametersParameter factor = new ParametersParameter().setName("factor").setValueDecimal(converted.setScale(2, RoundingMode.HALF_UP));
    ParametersParameter scalar = new ParametersParameter().setName("scalar").setValueString(converted.toString());
    parameters.setParameter(List.of(result, factor, scalar));
    return parameters;
  }
}
