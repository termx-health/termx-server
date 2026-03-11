package com.kodality.zmei.fhir.resource;

import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.util.Lists;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class DomainResource extends Resource {
  private Narrative text;
  private List<Resource> contained;
  private List<Extension> extension;
  private List<Extension> modifierExtension;

  public DomainResource(String resourceType) {
    super(resourceType);
  }

  public Optional<Extension> getExtension(String url) {
    return getExtensions(url).findFirst();
  }

  public Stream<Extension> getExtensions(String url) {
    return extension == null || url == null ? Stream.empty() : extension.stream().filter(e -> e.getUrl() != null && e.getUrl().equals(url));
  }

  public DomainResource addExtension(Extension extension) {
    this.extension = Lists.add(this.extension, extension);
    return this;
  }

  public DomainResource addContained(Resource c) {
    this.contained = Lists.add(this.contained, c);
    return this;
  }

  public Reference addContained(String id, Resource c) {
    c.setId(id);
    this.contained = Lists.add(this.contained, c);
    return new Reference("#" + id);
  }

  public <T extends Resource> T findContained(Reference reference) {
    return findContained(reference.extractIdFromReference(), reference.extractTypeFromReference());
  }

  @SuppressWarnings("unchecked")
  public <T extends Resource> T findContained(String id, String type) {
    if (contained == null) {
      return null;
    }
    String idd = id.startsWith("#") ? id.substring(1) : id;

    Resource containedResource = contained.stream()
        .filter(resource -> (type == null || type.equalsIgnoreCase(resource.getResourceType())) && idd.equals(resource.getId()))
        .findFirst().orElse(null);
    return (T) containedResource;
  }
}
