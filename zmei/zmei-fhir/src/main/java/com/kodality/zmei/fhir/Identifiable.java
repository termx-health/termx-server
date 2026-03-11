package com.kodality.zmei.fhir;

import com.kodality.zmei.fhir.datatypes.Identifier;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface Identifiable {

  List<Identifier> getIdentifier();

  default Optional<Identifier> getIdentifier(String system) {
    return getIdentifiers(system).findFirst();
  }

  default Stream<Identifier> getIdentifiers(String system) {
    return getIdentifier() == null || system == null ? Stream.empty()
        : getIdentifier().stream().filter(i -> system.equals(i.getSystem()));
  }

}
