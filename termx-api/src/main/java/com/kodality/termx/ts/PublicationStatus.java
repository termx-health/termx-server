package com.kodality.termx.ts;

import java.util.HashMap;
import java.util.Map;

public interface PublicationStatus {
  String draft = "draft";
  String active = "active";
  String retired = "retired";

  static String getStatus(String status) {
    Map<String, String> statusMap = new HashMap<>();

    statusMap.put("A", "active");
    statusMap.put("1", "active");
    statusMap.put("true", "active");
    statusMap.put("active", "active");

    statusMap.put("R", "retired");
    statusMap.put("0", "retired");
    statusMap.put("false", "retired");
    statusMap.put("deprecated", "retired");
    statusMap.put("retired", "retired");

    statusMap.put("P", "draft");
    statusMap.put("D", "draft");
    statusMap.put("experimental", "draft");
    statusMap.put("draft", "draft");

    return statusMap.getOrDefault(status, PublicationStatus.draft);
  }
}
