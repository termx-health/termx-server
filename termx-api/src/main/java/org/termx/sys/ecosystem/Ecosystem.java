package org.termx.sys.ecosystem;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class Ecosystem {
  private Long id;
  private String code;
  private LocalizedName names;
  private String formatVersion = "1";
  private String description;
  private boolean active;
  private List<Long> serverIds;
}
