package com.kodality.termserver.valueset;

import com.kodality.commons.model.LocalizedName;
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
  private LocalizedName names;
  private String rule;
  private String description;
  private String status;

  private List<ValueSetVersion> versions;
}
