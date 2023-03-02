package com.kodality.termserver.project;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ProjectOverview {
  private String code;
  private ProjectOverviewPackage pack;
  private List<String> codeSystem;
  private List<String> valueSet;
  private List<String> mapSet;
  private List<String> page;
  private List<String> structureDefinition;
  private Map<String, Map<String, List<String>>> sourceOfTruth;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProjectOverviewPackage {
    private String code;
    private String version;
  }
}
