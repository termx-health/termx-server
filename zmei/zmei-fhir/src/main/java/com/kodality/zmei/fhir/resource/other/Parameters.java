package com.kodality.zmei.fhir.resource.other;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.util.Lists;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Parameters extends Resource {
  private List<ParametersParameter> parameter;

  public Parameters() {
    super(ResourceType.parameters);
  }

  public Parameters addParameter(ParametersParameter p) {
    this.parameter = Lists.add(this.parameter, p);
    return this;
  }

  public Optional<ParametersParameter> findParameter(String name) {
    return parameter == null ? Optional.empty() : parameter.stream().filter(pp -> name.equals(pp.getName())).findFirst();
  }

  public ParametersParameter getParameter(String name) {
    return findParameter(name).orElse(null);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ParametersParameter extends BackboneElement {
    private String name;
    private Resource resource;
    private List<ParametersParameter> part;

    public ParametersParameter() {
    }

    public ParametersParameter(String name) {
      this.name = name;
    }

    // full specification of Parameters.parameter supports 50 value types,
    // let's have only most frequently used ones
    private Boolean valueBoolean;
    private String valueCode;
    private OffsetDateTime valueDateTime;
    private BigDecimal valueDecimal;
    private Integer valueInteger;
    private String valueString;
    private CodeableConcept valueCodeableConcept;
    private Coding valueCoding;
    private Identifier valueIdentifier;
    private Period valuePeriod;
    private String valueUri;
    private String valueUrl;
    private String valueUuid;
    private String valueCanonical;
    private Attachment valueAttachment;

    public ParametersParameter addPart(ParametersParameter p) {
      this.part = Lists.add(this.part, p);
      return this;
    }

    public Optional<ParametersParameter> findPart(String name) {
      return part == null ? Optional.empty() : part.stream().filter(pp -> name.equals(pp.getName())).findFirst();
    }

    public ParametersParameter getPart(String name) {
      return findPart(name).orElse(null);
    }

  }
}
