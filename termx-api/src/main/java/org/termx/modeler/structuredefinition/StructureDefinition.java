package org.termx.modeler.structuredefinition;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.termx.ts.ContactDetail;
import org.termx.ts.Copyright;
import org.termx.ts.UseContext;

@Getter
@Setter
@Accessors(chain = true)
public class StructureDefinition {
  private Long id;
  private String url;
  private String code;
  private String name;
  private String parent;
  private String publisher;

  private LocalizedName title;
  private LocalizedName description;
  private LocalizedName purpose;
  private Copyright copyright;
  private List<Identifier> identifiers;
  private List<ContactDetail> contacts;
  private List<UseContext> useContext;
  private String hierarchyMeaning;
  private Boolean experimental;

  /** Populated from current or requested version when loading. */
  private String content;
  private String contentType;
  private String contentFormat;
  private String version;
  private String fhirId;
  private String status;
  private java.time.OffsetDateTime releaseDate;
}
