package com.kodality.termx.ts.valueset;

import com.kodality.termx.ts.VersionReference;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetVersionReference extends VersionReference<ValueSetVersionReference> {
  private String valueSet;
}
