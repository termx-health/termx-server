package com.kodality.termserver.codesystem;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystem {
  private String id;
  private String uri;
  private Map<String, String> names;
  private String description;

  private List<EntityProperty> properties;
  private List<CodeSystemVersion> versions;
}
