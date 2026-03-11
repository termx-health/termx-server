package com.kodality.zmei.fhir;

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
public class Element extends Any {
  private String id;
  private List<Extension> extension;

  public Optional<Extension> getExtension(String url) {
    return getExtensions(url).findFirst();
  }

  public Stream<Extension> getExtensions(String url) {
    return extension == null || url == null ? Stream.empty() : extension.stream().filter(e -> url.equals(e.getUrl()));
  }

  public <T extends Element> T addExtension(Extension extension) {
    this.extension = Lists.add(this.extension, extension);
    return (T) this;
  }
}
