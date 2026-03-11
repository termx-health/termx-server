package com.kodality.zmei.fhir.resource.other;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Linkage extends DomainResource {
  private Boolean active;
  private Reference author;
  private List<LinkageItem> item;

  public Linkage() {
    super(ResourceType.linkage);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class LinkageItem extends BackboneElement {
    private String type;
    private Reference resource;
  }
}
