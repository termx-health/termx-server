package org.termx.sys.ecosystem;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EcosystemQueryParams extends QueryParams {
  private String ids;
  private String codes;
  private String textContains;

  private List<Long> permittedIds;
}
