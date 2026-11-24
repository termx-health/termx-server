package org.termx.ts.codesystem;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import org.termx.ts.valueset.ValueSetVersionReference;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class CodeSystemVersion extends CodeSystemVersionReference {
  private String codeSystem;
  private List<String> supportedLanguages;
  private LocalizedName description;
  private String algorithm;
  /**
   * How this version's concept hierarchy is written in its FHIR resource: {@code nested} — the
   * export termx had before the concept list was flattened, restored unchanged — or {@code flat}.
   * {@code null} leaves it to the server, which derives one that is both lossless and valid for the
   * data at hand (see {@code CodeSystemFhirMapper}). Published as the
   * {@code codesystem-hierarchy-shape} extension; the stored hierarchy itself does not change.
   */
  private String hierarchyShape;
  private LocalDate releaseDate;
  private LocalDate expirationDate;
  private OffsetDateTime created;
  private List<Identifier> identifiers;

  private String baseCodeSystem;
  private String baseCodeSystemUri;

  private ValueSetVersionReference valueSet;

  private Integer conceptsTotal;

  private List<CodeSystemEntityVersion> entities;
}
