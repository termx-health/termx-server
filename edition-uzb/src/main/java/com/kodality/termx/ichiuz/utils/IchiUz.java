package com.kodality.termx.ichiuz.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IchiUz {
  @JsonProperty(value = "group")
  private String group;
  @JsonProperty(value = "subgroup")
  private String subgroup;
  @JsonProperty(value = "system")
  private String system;
  @JsonProperty(value = "target")
  private String target;
  @JsonProperty(value = "action")
  private String action;
  @JsonProperty(value = "means")
  private String means;
  @JsonProperty(value = "ichi_code")
  private String code;
  @JsonProperty(value = "descriptor_uz-Cyrl")
  private String descriptorUzCyrl;
  @JsonProperty(value = "descriptor_uz")
  private String descriptorUz;
  @JsonProperty(value = "descriptor_ru")
  private String descriptorRu;
  @JsonProperty(value = "descriptor_en")
  private String descriptorEn;
  @JsonProperty(value = "definition_uz-Cyrl")
  private String definitionUzCyrl;
  @JsonProperty(value = "definition_uz")
  private String definitionUz;
  @JsonProperty(value = "definition_ru")
  private String definitionRu;
  @JsonProperty(value = "definition_en")
  private String definitionEn;
}
