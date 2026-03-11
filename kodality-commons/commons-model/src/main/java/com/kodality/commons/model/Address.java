package com.kodality.commons.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

@Deprecated
@Getter
@Setter
public class Address {
  private List<Identifier> identifiers;
  private String text;
  private String country;
  private String line;
  private String county;
  private String city;
  private String district;
  private String street;
  private String houseNumber;
  private String apartmentNumber;
  private String postalCode;

  @JsonIgnore
  public Optional<String> findIdentifier(String system) {
    if (identifiers == null || system == null) {
      return Optional.empty();
    }
    return identifiers.stream()
        .filter(identifier -> system.equals(identifier.getSystem()))
        .map(identifier -> identifier.getValue())
        .findFirst();
  }

  public void addIdentifier(String system, String value) {
    if (identifiers == null) {
      identifiers = new ArrayList<>();
    }
    identifiers.add(new Identifier(system, value));
  }
}
