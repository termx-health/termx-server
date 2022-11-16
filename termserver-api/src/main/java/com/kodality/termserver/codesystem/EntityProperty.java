package com.kodality.termserver.codesystem;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class EntityProperty {
  private Long id;
  private String name;
  private String type;
  private String description;
  private String status;

  private Integer orderNumber;
  private boolean preferred;
  private boolean required;

  private OffsetDateTime created;

  private Long supplementId;
}
