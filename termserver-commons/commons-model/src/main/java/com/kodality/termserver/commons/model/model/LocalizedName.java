package com.kodality.termserver.commons.model.model;

import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;

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
