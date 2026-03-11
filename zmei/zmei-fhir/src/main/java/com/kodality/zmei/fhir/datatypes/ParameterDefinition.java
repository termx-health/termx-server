package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ParameterDefinition extends Element {
  private String name;
  private String use;
  private Integer min;
  private String max;
  private String documentation;
  private String code;
  private String profile;
}
