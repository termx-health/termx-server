package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Reference extends Element {
  private String reference;
  private String type;
  private Identifier identifier;
  private String display;

  public Reference() {
  }

  public Reference(String type, String reference) {
    Objects.requireNonNull(type);
    this.type = type;
    this.reference = type + "/" + reference;
  }

  public Reference(String reference) {
    this.reference = reference;
  }

  public Reference(Identifier identifier) {
    this.identifier = identifier;
  }

  public String extractTypeFromReference() {
    if (reference == null) {
      return null;
    }

    String[] tokens = reference.split("/");
    return tokens.length > 1 ? tokens[tokens.length - 2] : null;
  }

  public String extractIdFromReference() {
    if (reference == null) {
      return null;
    }

    return reference.substring(reference.lastIndexOf('/') + 1);
  }

  public String extractContainedIdFromReference() {
    if (reference == null) {
      return null;
    }

    int slashIndex = reference.lastIndexOf('/');
    if (slashIndex >= 0) {
      return extractIdFromReference();
    }
    if (reference.startsWith("#")) {
      return reference.substring(1);
    }

    return reference;
  }
}
