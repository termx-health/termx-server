/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kodality.termserver.fhir;

import com.kodality.commons.util.resource.ResourcesLocator;
import com.kodality.kefhir.core.service.conformance.ConformanceResourceLoader;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.io.ResourceLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Resource;

@Slf4j
@Singleton
@Replaces(ConformanceResourceLoader.class)
@RequiredArgsConstructor
public class ConformanceInitializer implements ConformanceResourceLoader {
  private final ResourceFormatService resourceFormatService;
  private final Map<String, List<Resource>> resources = new HashMap<>();
  private final ResourceLoader resourceLoader;

  @PostConstruct
  public void init() {
    resourceLoader.getResources("conformance").flatMap(url -> ResourcesLocator.readResources(url.toString() + "/**")).forEach(res -> {
      Resource resource = this.resourceFormatService.parse(res.getContentString());
      if (resource.getResourceType().name().equals("Bundle")) {
        ((Bundle) resource).getEntry().forEach(e -> {
          resources.computeIfAbsent(e.getResource().getResourceType().name(), (k) -> new ArrayList<>()).add(e.getResource());
        });
      } else {
        resources.computeIfAbsent(resource.getResourceType().name(), (k) -> new ArrayList<>()).add(resource);
      }
    });
  }

  @SuppressWarnings("unchecked")
  public <T extends Resource> List<T> load(String name) {
    return (List<T>) resources.getOrDefault(name, new ArrayList<>());
  }

}
