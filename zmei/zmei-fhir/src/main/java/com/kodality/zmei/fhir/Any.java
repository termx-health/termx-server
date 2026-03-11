package com.kodality.zmei.fhir;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class Any {
  // @Getter(AccessLevel.NONE)
  // @Setter(AccessLevel.NONE)
  @JsonIgnore
  protected Map<String, Element> notSoPrimitives = new HashMap<>();

  // #see PrimitiveExtensionDeserializer
//  protected void setPrimitiveElementJackson(String field, Element element) {
//    if (field.startsWith("_")) {
//      field = field.replace("_", "");
//      notSoPrimitives.put(field, element);
//    }
//  }

  @JsonAnyGetter
  protected Map<String, Element> getPrimitiveElementsJackson() {
    return notSoPrimitives.entrySet().stream().collect(Collectors.toMap(e -> "_" + e.getKey(), e -> e.getValue()));
  }

  public <T> T setPrimitiveElement(String field, Element element) {
    notSoPrimitives.put(field, element);
    return (T) this;
  }

  public Element getPrimitiveElement(String field) {
    return notSoPrimitives.get(field);
  }

  public <T> T addPrimitiveExtension(String field, Extension extension) {
    if (extension != null) {
      notSoPrimitives.computeIfAbsent(field, f -> new Element()).addExtension(extension);
    }
    return (T) this;
  }

  public <T> T setPrimitiveExtensions(String field, List<Extension> extensions) {
    notSoPrimitives.computeIfAbsent(field, f -> new Element()).setExtension(extensions);
    return (T) this;
  }

}
