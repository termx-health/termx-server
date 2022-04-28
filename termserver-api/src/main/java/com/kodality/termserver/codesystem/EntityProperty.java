package com.kodality.termserver.codesystem;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EntityProperty {
  private Long id;
  private String name;
  private String type;
  private String description;
  private String status;
  private OffsetDateTime created;
}
