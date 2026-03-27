package org.termx.sys.ecosystem;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class EcosystemPublicResponse {
  private Long id;
  private String code;
  private LocalizedName names;
  private EcosystemPayload ecosystem;

  @Getter
  @Setter
  @Accessors(chain = true)
  @Introspected
  public static class EcosystemPayload {
    private String formatVersion;
    private String description;
    private List<Map<String, Object>> servers;
  }
}
