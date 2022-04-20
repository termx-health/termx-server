package com.kodality.termserver.commons.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class JsonUtil {

  private static ObjectMapper mapper;
  private static ObjectWriter prettyWriter;

  public static synchronized ObjectMapper getObjectMapper() {
    if (mapper == null) {
      mapper = buildCommonObjectMapper();
    }

    return mapper;
  }

  public static ObjectMapper buildCommonObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.setSerializationInclusion(Include.NON_NULL);
    mapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  public static ObjectWriter getPrettyWriter() {
    if (prettyWriter == null) {
      prettyWriter = getObjectMapper().writer().withDefaultPrettyPrinter();
    }
    return prettyWriter;
  }


  public static String toJson(Object object) {
    return toJson(object, getObjectMapper());
  }

  public static String toJson(Object object, ObjectMapper om) {
    try {
      return object == null ? null : om.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage());
    }
  }

  public static String toPrettyJson(Object object) {
    try {
      return object == null ? null : getPrettyWriter().writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage());
    }
  }

  public static <T> T fromJson(String json, Class<T> clazz, ObjectMapper objectMapper) {
    try {
      return StringUtils.isEmpty(json) ? null : objectMapper.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage());
    }
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    return fromJson(json, clazz, getObjectMapper());
  }

  public static <T> T fromJson(String json, TypeReference<T> reference) {
    try {
      return json == null ? null : getObjectMapper().readValue(json, reference);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage());
    }
  }

  public static <T> T fromJson(String json, JavaType type) {
    try {
      return json == null ? null : getObjectMapper().readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> Map<String, T> toMap(String json) {
    if (json == null) {
      return null;
    }
    try {
      return getObjectMapper().readValue(json, HashMap.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage());
    }
  }

  public static <T> T read(String json, String path) {
    if (json == null) {
      return null;
    }
    try {
      return JsonPath.read(json, path);
    } catch (PathNotFoundException e) {
      return null;
    }
  }

  public static String readString(String json, String path) {
    Object o = read(json, path);
    return o == null ? null : o.toString();
  }

  public static BigDecimal readBigDecimal(String json, String path) {
    Object o = read(json, path);
    if (o == null) {
      return null;
    }
    if (o instanceof String) {
      return new BigDecimal((String) o).setScale(2, RoundingMode.HALF_UP);
    }
    if (o instanceof Integer) {
      return new BigDecimal((Integer) o).setScale(2, RoundingMode.HALF_UP);
    }
    return BigDecimal.valueOf((Double) o).setScale(2, RoundingMode.HALF_UP);
  }

  public static JavaType getListType(Class<?> clazz) {
    return getObjectMapper().getTypeFactory().constructCollectionType(List.class, clazz);
  }

  public static JavaType getMapType(Class<?> valueClass) {
    return getObjectMapper().getTypeFactory().constructMapType(Map.class, Object.class, valueClass);
  }

  public static JavaType getParametricType(Class<?> baseClass, Class<?> parameters) {
    return getObjectMapper().getTypeFactory().constructParametricType(baseClass, parameters);
  }
}
