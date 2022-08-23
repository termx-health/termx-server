package com.kodality.termserver.codesystem;

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
public class CodeSystem {
  private String id;
  private String uri;
  private LocalizedName names;
  private String content;
  private List<ContactDetail> contacts;
  private String caseSensitive;
  private String narrative;
  private String description;

  private String baseCodeSystem;

  private List<Concept> concepts;
  private List<EntityProperty> properties;
  private List<CodeSystemVersion> versions;
}
