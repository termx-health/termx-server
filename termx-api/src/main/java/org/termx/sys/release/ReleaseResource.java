package org.termx.sys.release;

import com.kodality.commons.model.LocalizedName;
import org.termx.sys.ResourceReference;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ReleaseResource extends ResourceReference {
  private Long id;
  private LocalizedName resourceNames;
}
