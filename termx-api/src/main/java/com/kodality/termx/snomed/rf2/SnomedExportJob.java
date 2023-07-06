package com.kodality.termx.snomed.rf2;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SnomedExportJob {
  private String id;
  private String branchPath;
  private boolean conceptsAndRelationshipsOnly;
  private String filenameEffectiveDate;
  private boolean legacyZipNaming;
  private List<String> moduleIds;
  private List<String> refsetIds;
  private OffsetDateTime startDate;
  private String startEffectiveTime;
  private String transientEffectiveTime;
  private String type;
  private boolean unpromotedChangesOnly;
}
