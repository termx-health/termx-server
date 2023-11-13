package com.kodality.termx.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodality.commons.util.JsonUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class ObjectUtil {

  public static String removeEmptyAttributes(String jsonStr) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode rootNode = (ObjectNode) objectMapper.readTree(jsonStr);

    JsonNode cleanedJson = removeEmptyFields(rootNode);
    return JsonUtil.toPrettyJson(JsonUtil.fromJson(objectMapper.writeValueAsString(cleanedJson), Object.class));
  }


  private static ObjectNode removeEmptyFields(final ObjectNode jsonNode) {
    ObjectNode ret = new ObjectMapper().createObjectNode();
    Iterator<Entry<String, JsonNode>> iter = jsonNode.fields();

    while (iter.hasNext()) {
      Entry<String, JsonNode> entry = iter.next();
      String key = entry.getKey();
      JsonNode value = entry.getValue();

      if (value instanceof ObjectNode) {
        Map<String, ObjectNode> map = new HashMap<>();
        map.put(key, removeEmptyFields((ObjectNode)value));
        ret.setAll(map);
      }
      else if (value instanceof ArrayNode && !value.isEmpty()) {
        ret.set(key, removeEmptyFields((ArrayNode)value));
      }
      else if (value.asText() != null && !value.asText().isEmpty()) {
        ret.set(key, value);
      }
    }

    return ret;
  }

  private static ArrayNode removeEmptyFields(ArrayNode array) {
    ArrayNode ret = new ObjectMapper().createArrayNode();
    Iterator<JsonNode> iter = array.elements();

    while (iter.hasNext()) {
      JsonNode value = iter.next();

      if (value instanceof ArrayNode) {
        ret.add(removeEmptyFields((ArrayNode)(value)));
      }
      else if (value instanceof ObjectNode) {
        ret.add(removeEmptyFields((ObjectNode)(value)));
      }
      else if (value != null && !value.textValue().isEmpty()){
        ret.add(value);
      }
    }

    return ret;
  }
}
