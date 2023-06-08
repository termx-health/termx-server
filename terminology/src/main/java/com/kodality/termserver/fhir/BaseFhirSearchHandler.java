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

import com.kodality.kefhir.core.api.resource.ResourceSearchHandler;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class BaseFhirSearchHandler implements ResourceSearchHandler {
  private final Map<String, BaseFhirResourceStorage> storages;

  public BaseFhirSearchHandler(List<BaseFhirResourceStorage> storages) {
    this.storages = storages.stream().collect(Collectors.toMap(s -> s.getResourceType(), s -> s));
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    if (!storages.containsKey(criteria.getType())) {
      throw new IllegalArgumentException(criteria.getType() + " not supported");
    }
    return storages.get(criteria.getType()).search(criteria);
  }

}
