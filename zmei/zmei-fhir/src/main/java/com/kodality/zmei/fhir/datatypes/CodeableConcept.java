package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import com.kodality.zmei.fhir.util.Lists;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import static java.util.stream.Collectors.toList;

@Getter
@Setter
@Accessors(chain = true)
@ToString
public class CodeableConcept extends Element {
  private List<Coding> coding;
  private String text;

  public CodeableConcept() {
  }

  public CodeableConcept(Coding... coding) {
    this.coding = Arrays.asList(coding);
  }

  public static CodeableConcept fromCodes(String... codes) {
    return new CodeableConcept().setCoding(
        Stream.of(codes)
            .filter(Objects::nonNull)
            .map(Coding::new)
            .collect(toList())
    );
  }

  public static Stream<String> getCodes(List<CodeableConcept> cc) {
    return cc == null ? Stream.empty() : cc.stream().flatMap(CodeableConcept::getCodes);
  }

  public static Stream<String> getCodes(CodeableConcept cc) {
    return Stream.ofNullable(cc)
        .filter(c -> c.getCoding() != null)
        .flatMap(c -> c.getCoding().stream())
        .map(Coding::getCode);
  }

  public String getCode(String system) {
    if (this.coding == null) {
      return null;
    }
    return this.coding.stream()
        .filter(coding -> system.equalsIgnoreCase(coding.getSystem()))
        .findFirst()
        .map(Coding::getCode)
        .orElse(null);
  }

  public String getDisplay(String system) {
    if (this.coding == null) {
      return null;
    }
    return this.coding.stream()
        .filter(coding -> system.equalsIgnoreCase(coding.getSystem()))
        .findFirst()
        .map(Coding::getDisplay)
        .orElse(null);
  }

  public CodeableConcept addCoding(Coding c) {
    coding = Lists.add(coding, c);
    return this;
  }
}
