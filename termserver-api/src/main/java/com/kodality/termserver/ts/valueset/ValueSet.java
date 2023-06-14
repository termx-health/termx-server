package com.kodality.termserver.ts.valueset;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.ts.ContactDetail;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class ValueSet {
  private String id;
  private String uri;
  private String publisher;
  private LocalizedName title;
  private LocalizedName name;
  private LocalizedName description;
  private LocalizedName purpose;
  private String narrative;
  private Boolean experimental;
  private List<Identifier> identifiers;
  private List<ContactDetail> contacts;
  private Object copyright;
  private Object permissions;

  private List<ValueSetVersion> versions;

  private List<ValueSetSnapshot> snapshots;
}
