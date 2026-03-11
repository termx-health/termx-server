package com.kodality.zmei.fhir.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.kodality.zmei.fhir.Any;
import com.kodality.zmei.fhir.Element;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PrimitiveExtensionAwareDeserializer extends BeanDeserializer {
  private final JsonDeserializer<?> defaultDeserializer;

  public PrimitiveExtensionAwareDeserializer(BeanDeserializer defaultDeserializer) {
    super(defaultDeserializer);
    this.defaultDeserializer = defaultDeserializer;
  }

  @Override
  public Any deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    JsonNode node = mapper.readTree(p);
    JsonParser p2 = mapper.treeAsTokens(node);
    p2.nextToken();
    Any result = (Any) defaultDeserializer.deserialize(p2, ctxt);
    Map<String, Object> map = mapper.treeToValue(node, HashMap.class);
    map.keySet().stream().filter(k -> k.startsWith("_")).forEach(k -> {
      String f = k.replace("_", "");
      result.setPrimitiveElement(f, mapper.convertValue(map.get(k), Element.class));
    });
    return result;
  }
}
