package com.kodality.termx.ts.valueset;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class ValueSetVersion extends ValueSetVersionReference {
  private String valueSet;
  private List<String> supportedLanguages;
  private LocalizedName description;
  private String status;
  private LocalDate releaseDate;
  private LocalDate expirationDate;
  private OffsetDateTime created;
  private String algorithm;
  private ValueSetVersionRuleSet ruleSet;

  //always loaded but does not update from here
  private ValueSetSnapshot snapshot;
}
