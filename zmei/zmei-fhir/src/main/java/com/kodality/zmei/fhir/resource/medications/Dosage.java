package com.kodality.zmei.fhir.resource.medications;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Element;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Range;
import com.kodality.zmei.fhir.datatypes.Ratio;
import com.kodality.zmei.fhir.datatypes.Timing;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Dosage extends BackboneElement {
  private Integer sequence;
  private String text;
  private List<CodeableConcept> additionalInstruction;
  private String patientInstruction;
  private Timing timing;
  private Boolean asNeeded;
  private CodeableConcept asNeededFor;
  private CodeableConcept site;
  private CodeableConcept route;
  private CodeableConcept method;
  private List<DosageDoseAndRate> doseAndRate;
  private List<Ratio> maxDosePerPeriod;
  private Quantity maxDosePerAdministration;
  private Quantity maxDosePerLifetime;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DosageDoseAndRate extends Element {
    private CodeableConcept type;
    private Range doseRange;
    private Quantity doseQuantity;
    private Ratio rateRatio;
    private Range rateRange;
    private Quantity rateQuantity;
  }
}
