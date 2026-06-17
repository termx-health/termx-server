package org.termx.ts.codesystem;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemEntityVersionQueryParams extends QueryParams {
  private String ids;
  private String code;
  private String codesNe;
  private String codeContains;
  private String descriptionContains;
  private String textContains;
  private String status;
  private String codeSystem;
  private List<String> permittedCodeSystems;
  private Long codeSystemEntityId;
  private String codeSystemEntityIds;
  private Long codeSystemVersionId;
  private String codeSystemVersions; //cs1|v1,cs2|v1
  private String codeSystemVersion;
  private String codeSystemUri;
  private Boolean unlinked;
  // When false, skip appending designations/properties matched by code from a code system's
  // base_code_system. Value set expansion sets this off to avoid materializing every dependent
  // code system's designations for every concept (issue #36). Supplements are unaffected — they
  // link via baseEntityVersionId, a separate path.
  private boolean decorateBaseCodeSystem = true;
}
