package com.kodality.termx.snomed.refset;

import com.kodality.termx.snomed.refset.SnomedRefsetResponse.SnomedRefsetItem;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedRefsetMemberResponse {
  private List<SnomedRefsetItem> items;
  private Long total;
}
