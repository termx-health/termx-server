package com.kodality.termx.implementationguide.ig;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.Copyright;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuide {
  private String id;
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
