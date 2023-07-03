package com.kodality.termx.ts.mapset;

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
public class MapSet {
  private String id;
  private String uri;
  private LocalizedName names;
  private List<ContactDetail> contacts;
  private String narrative;
  private String description;
  private String sourceValueSet;
  private String targetValueSet;
  private List<String> sourceCodeSystems;
  private List<String> targetCodeSystems;

  private List<MapSetVersion> versions;
  private List<MapSetAssociation> associations;
}
