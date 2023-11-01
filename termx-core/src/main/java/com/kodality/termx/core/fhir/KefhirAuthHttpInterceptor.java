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

import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.InteractionType;
import com.kodality.kefhir.rest.filter.KefhirRequestExecutionInterceptor;
import com.kodality.kefhir.rest.model.KefhirRequest;
import com.kodality.termx.core.auth.AuthorizationFilter;
import com.kodality.termx.core.auth.SessionStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@RequiredArgsConstructor
@Singleton
public class KefhirAuthHttpInterceptor implements KefhirRequestExecutionInterceptor {
  private final Map<String, String> resourcePrivileges;

  public KefhirAuthHttpInterceptor(List<BaseFhirResourceHandler> storages, AuthorizationFilter authorizationFilter) {
    resourcePrivileges = storages.stream().collect(Collectors.toMap(BaseFhirResourceHandler::getResourceType, BaseFhirResourceHandler::getPrivilegeName));
    authorizationFilter.addPublicEndpoint("/fhir"); // do not use default authorization.
  }

  @Override
  public void beforeExecute(KefhirRequest request) {
    String interaction = request.getOperation().getInteraction();
    String type = request.getType();
    String id = BaseFhirMapper.parseCompositeId(StringUtils.substringBefore(request.getPath(), "/"))[0];
    if (Objects.equals(InteractionType.SEARCHTYPE, interaction)) {
      return;
    }
    if (Objects.equals(InteractionType.READ, interaction) &&
        !SessionStore.require().hasPrivilege(id + "." + resourcePrivileges.get(type) + ".view")) {
      throw new FhirException(403, IssueType.FORBIDDEN, id + "." + type + "." + interaction + " not allowed");
    }
    if (Objects.equals(InteractionType.CREATE, interaction)
        && !SessionStore.require().hasPrivilege("*." + resourcePrivileges.get(type) + ".edit")) {
      throw new FhirException(403, IssueType.FORBIDDEN, id + "." + type + "." + interaction + " not allowed");
    }
    if (Objects.equals(InteractionType.UPDATE, interaction)
        && !SessionStore.require().hasPrivilege(id + "." + resourcePrivileges.get(type) + ".edit")) {
      throw new FhirException(403, IssueType.FORBIDDEN, id + "." + type + "." + interaction + " not allowed");
    }
    if (Objects.equals(InteractionType.OPERATION, interaction) && request.getPath().contains("/") /* instance operation */) {
      //check at least view privilege for instance operations. everything else  manually in operation implementation
      if (!SessionStore.require().hasPrivilege(id + "." + resourcePrivileges.get(type) + ".view")) {
        throw new FhirException(403, IssueType.FORBIDDEN, request.getPath() + "." + type + " not allowed");
      }
    }
  }

}
