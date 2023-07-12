package com.kodality.termx.ts.valueset;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.ContactDetail;
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
  private ValueSetSettings settings;

  private List<ValueSetVersion> versions;

  private List<ValueSetSnapshot> snapshots;

  @Getter
  @Setter
  private static class ValueSetSettings {
    private boolean reviewRequired;
    private boolean approvalRequired;
  }
}
