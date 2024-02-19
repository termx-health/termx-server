package com.kodality.termx.ts.valueset;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.ConfigurationAttribute;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.OtherTitle;
import com.kodality.termx.ts.Permissions;
import com.kodality.termx.ts.Topic;
import com.kodality.termx.ts.UseContext;
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
  private List<OtherTitle> otherTitle;
  private LocalizedName title;
  private LocalizedName description;
  private LocalizedName purpose;
  private Topic topic;
  private List<UseContext> useContext;
  private String narrative;
  private Boolean experimental;
  private String sourceReference;
  private String replaces;
  private List<Identifier> identifiers;
  private List<ConfigurationAttribute> configurationAttributes;
  private List<ContactDetail> contacts;
  private Copyright copyright;
  private Permissions permissions;

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
