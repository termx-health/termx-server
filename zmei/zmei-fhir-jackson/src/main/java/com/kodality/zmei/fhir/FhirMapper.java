package com.kodality.zmei.fhir;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.kodality.zmei.fhir.jackson.OffsetDateTimeDeserializer;
import com.kodality.zmei.fhir.jackson.ZmeiModule;
import com.kodality.zmei.fhir.jackson.ZonedDateTimeDeserializer;
import com.kodality.zmei.fhir.resource.Resource;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FhirMapper {
  private static ObjectMapper mapper;
  private static ObjectWriter prettyWriter;

  public static synchronized ObjectMapper getObjectMapper() {
    if (mapper == null) {
      mapper = new ObjectMapper();
      mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      mapper.setSerializationInclusion(Include.NON_EMPTY); // this will remove all empty lists and objects, as-well as strings. hapi does the same
      JavaTimeModule javaTimeModule = new JavaTimeModule();
      javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ISO_DATE_TIME));
      javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ISO_DATE_TIME));
      javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
      javaTimeModule.addDeserializer(ZonedDateTime.class, new ZonedDateTimeDeserializer());
      mapper.registerModule(javaTimeModule);
      mapper.registerModule(new ZmeiModule());
    }
    return mapper;
  }

  public static ObjectWriter getPrettyWriter() {
    if (prettyWriter == null) {
      prettyWriter = getObjectMapper().writer().withDefaultPrettyPrinter();
    }
    return prettyWriter;
  }

  public static String toJson(Object r) {
    return toJson(r, false);
  }

  public static String toJson(Object r, boolean pretty) {
    try {
      if (r == null) {
        return null;
      }
      LinkedHashMap<String, Object> temp = getObjectMapper().convertValue(r, LinkedHashMap.class); // also clears empty objects
      temp = reorder(temp);
      return pretty ? getPrettyWriter().writeValueAsString(temp) : getObjectMapper().writeValueAsString(temp);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage());
    }
  }

  public static <T extends Resource> T fromJson(String json, Class<T> clazz) {
    try {
      return json == null ? null : getObjectMapper().readValue(json, clazz);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage(), e);
    }
  }


  // forces primitive extensions (_field) to be ordered after primitive itself (field)
  private static LinkedHashMap<String, Object> reorder(LinkedHashMap<String, Object> map) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>(map.size());
    new LinkedHashSet<>(map.keySet()).stream().filter(k -> !k.startsWith("_")).forEach(k -> {
      Object v = map.remove(k);
      if (v instanceof Map) {
        result.put(k, reorder((LinkedHashMap<String, Object>) v));
        return;
      }
      if (v instanceof List) {
        result.put(k, ((List) v).stream().map(e -> {
          if (e instanceof Map) {
            return reorder((LinkedHashMap<String, Object>) e);
          }
          return e;
        }).collect(Collectors.toList()));
        return;
      }
      result.put(k, v);
      result.put("_" + k, map.remove("_" + k));
    });
    result.putAll(map);
    return result;
  }

}
