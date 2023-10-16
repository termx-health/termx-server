package com.kodality.termx.core.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.CollectionDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.util.Collection;

public class XmlWhitespaceModule extends SimpleModule {

  private static class CustomizedCollectionDeserializer extends CollectionDeserializer {

    public CustomizedCollectionDeserializer(CollectionDeserializer src) {
      super(src);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Object> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.VALUE_STRING && jp.getText().matches("^[\\r\\n\\t ]+$")) {
        return (Collection<Object>) _valueInstantiator.createUsingDefault(ctxt);
      }
      return super.deserialize(jp, ctxt);
    }

    @Override
    public CollectionDeserializer createContextual(DeserializationContext ctx, BeanProperty property) throws JsonMappingException {
      return new CustomizedCollectionDeserializer(super.createContextual(ctx, property));
    }
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.addBeanDeserializerModifier(new BeanDeserializerModifier() {
      @Override
      public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config, CollectionType type, BeanDescription beanDesc,
                                                              JsonDeserializer<?> deserializer) {
        if (deserializer instanceof CollectionDeserializer) {
          return new CustomizedCollectionDeserializer((CollectionDeserializer) deserializer);
        }
        return super.modifyCollectionDeserializer(config, type, beanDesc, deserializer);
      }
    });
  }

}
