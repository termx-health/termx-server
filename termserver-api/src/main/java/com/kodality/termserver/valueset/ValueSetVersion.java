package com.kodality.termserver.valueset;

import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
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

  private ValueSetRuleSet ruleSet;
  private List<ValueSetConcept> concepts;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetConcept {
    private Long id;
    private Long orderNr;
    private Concept concept;
    private Designation display;
    private List<Designation> additionalDesignations;
  }
}
