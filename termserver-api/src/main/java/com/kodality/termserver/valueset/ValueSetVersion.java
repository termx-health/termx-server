package com.kodality.termserver.valueset;

import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class ValueSetVersion {
  private Long id;
  private String valueSet;
  private String version;
  private String source;
  private List<String> supportedLanguages;
  private String description;
  private String status;
  private LocalDate releaseDate;
  private LocalDate expirationDate;
  private OffsetDateTime created;

  private ValueSetVersionRuleSet ruleSet;
  private List<ValueSetVersionConcept> concepts;
}
