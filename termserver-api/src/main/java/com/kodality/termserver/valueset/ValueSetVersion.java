package com.kodality.termserver.valueset;

import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValueSetVersion {
  private Long id;
  private String valueSet;
  private String version;
  private List<String> supportedLanguages;
  private String description;
  private String status;
  private LocalDate releaseDate;
  private LocalDate expirationDate;
  private OffsetDateTime created;

  private List<Concept> concepts;
  private List<Designation> designations;
}
