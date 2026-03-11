package com.kodality.zmei.fhir.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.kodality.zmei.fhir.Any;
import com.kodality.zmei.fhir.resource.Resource;

public class ZmeiModule extends SimpleModule {

  public ZmeiModule() {
    addDeserializer(Resource.class, new ResourceDeserializer());
    setDeserializerModifier(new PrimitiveDeserializerModifier());
  }

  private static class PrimitiveDeserializerModifier extends BeanDeserializerModifier {
    @Override
    public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
      if (Any.class.isAssignableFrom(beanDesc.getBeanClass()) && deserializer instanceof BeanDeserializer) {
        return new PrimitiveExtensionAwareDeserializer((BeanDeserializer) deserializer);
      }
      return deserializer;
    }
  }
}
