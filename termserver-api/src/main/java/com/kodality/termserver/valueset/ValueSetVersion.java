package com.kodality.termserver.valueset;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValueSetVersion {
  private Long id;
  private String version;
  private String ruleValue;
  private List<String> supportedLanguages;
  private String description;
  private String status;
  private Date releaseDate;
  private OffsetDateTime created;
}
