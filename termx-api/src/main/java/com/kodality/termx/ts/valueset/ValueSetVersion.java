package com.kodality.termx.ts.valueset;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ValueSetVersion extends ValueSetVersionReference {
  private String preferredLanguage;
  private List<String> supportedLanguages;
  private LocalizedName description;
  private String status;
  private LocalDate releaseDate;
  private LocalDate expirationDate;
  private OffsetDateTime created;
  private String algorithm;
  private ValueSetVersionRuleSet ruleSet;
  private List<Identifier> identifiers;

  //always loaded but does not update from here
  private ValueSetSnapshot snapshot;
}
