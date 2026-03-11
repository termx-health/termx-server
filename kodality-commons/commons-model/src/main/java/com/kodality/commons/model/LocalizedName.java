package com.kodality.commons.model;

import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
public class LocalizedName extends HashMap<String, String> {

  public LocalizedName(Map<String, String> map) {
    if (map != null) {
      putAll(map);
    }
  }

  public LocalizedName add(String lang, String name) {
    put(lang, name);
    return this;
  }
}
