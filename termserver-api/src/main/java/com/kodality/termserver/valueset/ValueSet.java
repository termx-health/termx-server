package com.kodality.termserver.valueset;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.ContactDetail;
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
  private LocalizedName names;
  private List<ContactDetail> contacts;
  private String narrative;
  private String description;

  private List<ValueSetVersion> versions;
}
