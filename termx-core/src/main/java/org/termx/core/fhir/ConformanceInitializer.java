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
package org.termx.core.fhir;

import com.kodality.kefhir.core.service.conformance.loader.ConformanceLoader;
import com.kodality.kefhir.core.service.conformance.loader.ConformanceStaticLoader;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.model.Resource;

@Slf4j
@Singleton
@Replaces(ConformanceLoader.class)
@RequiredArgsConstructor
public class ConformanceInitializer extends ConformanceStaticLoader {
  private final List<TermxGeneratedConformanceProvider> generators;
  private final ResourceLoader resourceLoader;

  @Override
  public List<String> getResources() {
    List<String> resources = new ArrayList<>();
    
    // Use Micronaut's ResourceLoader to scan conformance directory
    try {
      resourceLoader.getResourceAsStream("classpath:conformance").ifPresent(stream -> {
        log.warn("Direct conformance directory access not supported, using pattern matching");
      });
      
      // Load known conformance files directly (list generated from JAR contents)
      String[] conformanceFiles = {
        "conformance/CapabilityStatement.json",
        "conformance/OperationDefinition-StructureMap-transform.json",
        "conformance/StructureDefinition-StructureMap.json",
        "conformance/OperationDefinition-CodeSystem-comapre.json",
        "conformance/OperationDefinition-CodeSystem-find-matches.json",
        "conformance/OperationDefinition-CodeSystem-lookup.json",
        "conformance/OperationDefinition-CodeSystem-subsumes.json",
        "conformance/OperationDefinition-CodeSystem-sync.json",
        "conformance/OperationDefinition-CodeSystem-validate-code.json",
        "conformance/OperationDefinition-ConceptMap-sync.json",
        "conformance/OperationDefinition-ConceptMap-translate.json",
        "conformance/OperationDefinition-ValueSet-expand.json",
        "conformance/OperationDefinition-ValueSet-sync.json",
        "conformance/OperationDefinition-ValueSet-validate-code.json",
        "conformance/StructureDefinition-CodeSystem.json",
        "conformance/StructureDefinition-ConceptMap.json",
        "conformance/StructureDefinition-OperationDefinition.json",
        "conformance/StructureDefinition-Parameters.json",
        "conformance/StructureDefinition-Resource.json",
        "conformance/StructureDefinition-ValueSet.json",
        "conformance/StructureDefinition-Provenance.json",
        "conformance/base/SearchParameter.json",
        "conformance/base/profiles-types.json"
      };
      
      for (String file : conformanceFiles) {
        resourceLoader.getResourceAsStream("classpath:" + file).ifPresent(stream -> {
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            resources.add(content);
            log.debug("Loaded conformance resource: {}", file);
          } catch (Exception e) {
            log.error("Failed to read conformance file: {}", file, e);
          }
        });
      }
      
      log.info("Loaded {} conformance resources", resources.size());
    } catch (Exception e) {
      log.error("Failed to load conformance resources", e);
    }
    
    return resources;
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
