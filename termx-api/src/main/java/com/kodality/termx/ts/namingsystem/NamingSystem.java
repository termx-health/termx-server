package com.kodality.termx.ts.namingsystem;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class NamingSystem {
  private String id;
  private LocalizedName names;
  private String kind;
  private String codeSystem;
  private String source;
  private String description;
  private List<NamingSystemIdentifier> identifiers;
  private String status;
  private OffsetDateTime created;
}
