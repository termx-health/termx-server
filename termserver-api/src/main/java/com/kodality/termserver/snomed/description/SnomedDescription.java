package com.kodality.termserver.snomed.description;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedDescription {
  private Long id;
  private String descriptionId;
  private String conceptId;
  private String lang;
  private String term;
  private String moduleId;
  private String typeId;
  private String caseSignificanceId;
  private String effectiveTime;
  private boolean active = true;
  private Map<String, String> acceptabilityMap;

  private String status;
  private String author;
  private String reviewer;
  private LocalDateTime created;
  private LocalDateTime reviewed;
}
