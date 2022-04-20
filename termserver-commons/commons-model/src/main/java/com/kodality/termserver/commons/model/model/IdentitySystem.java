package com.kodality.termserver.commons.model.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IdentitySystem {
  private Long id;
  private String code;
  private LocalizedName names;
  private String jurisdiction;
  private List<String> usage;
  private String cardinality;
  private String codingSystem;
  private String codingUrl;
}
