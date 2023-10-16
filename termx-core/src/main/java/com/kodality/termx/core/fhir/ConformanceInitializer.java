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
package com.kodality.termx.core.fhir;

import com.kodality.commons.util.resource.ResourcesLocator;
import com.kodality.kefhir.core.service.conformance.loader.ConformanceLoader;
import com.kodality.kefhir.core.service.conformance.loader.ConformanceStaticLoader;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.Resource;

@Singleton
@Replaces(ConformanceLoader.class)
@RequiredArgsConstructor
public class ConformanceInitializer extends ConformanceStaticLoader {
  private final List<TermxGeneratedConformanceProvider> generators;
  private final ResourceLoader resourceLoader;

  @Override
  public List<String> getResources() {
    return resourceLoader.getResources("conformance").flatMap(url -> ResourcesLocator.readResources(url.toString() + "/**")).map(
        com.kodality.commons.util.resource.Resource::getContentString).toList();
  }

  @SuppressWarnings("unchecked")
  public <T extends Resource> List<T> load(String name) {
      if (resources.containsKey(name)) {
        return (List<T>) resources.get(name);
      }
      return (List<T>) generators.stream().map(g -> g.generate(name)).filter(Objects::nonNull).toList();
  }

  public interface TermxGeneratedConformanceProvider {
    Resource generate(String name);
  }
}
