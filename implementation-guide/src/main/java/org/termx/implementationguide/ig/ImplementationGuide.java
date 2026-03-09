package org.termx.implementationguide.ig;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import org.termx.commons.UniqueResource;
import org.termx.implementationguide.ig.version.ImplementationGuideVersion;
import org.termx.ts.ContactDetail;
import org.termx.ts.Copyright;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuide extends UniqueResource<ImplementationGuide> {
  private String uri;
  private String publisher;
  private String name;
  private LocalizedName title;
  private LocalizedName description;
  private LocalizedName purpose;
  private String licence;
  private Boolean experimental;
  private List<Identifier> identifiers;
  private List<ContactDetail> contacts;
  private Copyright copyright;

  private List<ImplementationGuideVersion> versions;
}
