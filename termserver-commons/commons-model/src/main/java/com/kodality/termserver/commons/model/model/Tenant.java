package com.kodality.termserver.commons.model.model;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Tenant {
  private String id;
  private String name;
  private Map<String, String> properties;
}
