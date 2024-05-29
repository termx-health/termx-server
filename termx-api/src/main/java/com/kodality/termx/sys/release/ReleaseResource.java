package com.kodality.termx.sys.release;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.sys.ResourceReference;
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
