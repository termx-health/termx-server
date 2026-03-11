package com.kodality.zmei.fhir.resource;

import com.kodality.zmei.fhir.Element;
import com.kodality.zmei.fhir.datatypes.Coding;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Meta extends Element {
  private String versionId;
  private OffsetDateTime lastUpdated;
  private String source;
  private List<String> profile;
  private List<Coding> security;
  private List<Coding> tag;
}
