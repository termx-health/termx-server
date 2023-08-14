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
  private String name;
  private LocalizedName title;
  private LocalizedName description;
  private LocalizedName purpose;
  private String narrative;
  private Boolean experimental;
  private List<Identifier> identifiers;
  private List<ContactDetail> contacts;
  private ValueSetCopyright copyright;
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

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetCopyright {
    private String holder;
    private String jurisdiction;
    private String statement;
  }
}
