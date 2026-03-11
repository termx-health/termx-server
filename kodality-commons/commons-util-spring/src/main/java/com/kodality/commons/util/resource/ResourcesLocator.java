package com.kodality.commons.util.resource;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class ResourcesLocator {

  public static Optional<Resource> readResource(String path) {
    return readResources(path).findFirst();
  }

  /**
   * @param path may be directory.
   */
  public static Stream<Resource> readResources(String path) {
    return findResources(path).filter(p -> p.isReadable()).map(r -> new Resource(r.getFilename(), readResourceContent(r)));
  }

  private static Stream<org.springframework.core.io.Resource> findResources(String path) {
    try {
      PathMatchingResourcePatternResolver resolver =
          new PathMatchingResourcePatternResolver(ResourcesLocator.class.getClassLoader());
      return Stream.of(resolver.getResources(path));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] readResourceContent(org.springframework.core.io.Resource resource) {
    try {
      return IOUtils.toByteArray(resource.getInputStream());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
