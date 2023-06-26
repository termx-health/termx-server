package com.kodality.termserver.ts.mapset;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetQueryParams extends QueryParams {
  private String id;
  private String ids;
  private String idContains;
  private List<String> permittedIds;
  private String uri;
  private String uriContains;
  private String name;
  private String nameContains;
  private String description;
  private String descriptionContains;
  private String sourceValueSet;
  private String targetValueSet;

  private String text;
  private String textContains;

  private Long associationSourceId;
  private String associationSourceCode;
  private String associationSourceSystem;
  private String associationSourceSystemUri;
  private String associationSourceSystemVersion;
  private Long associationTargetId;
  private String associationTargetCode;
  private String associationTargetSystem;
  private String associationTargetSystemUri;
  private String associationTargetSystemVersion;
  private boolean associationsDecorated;

  private String versionVersion;
  private boolean versionsDecorated;

  private String lang;

  private Long spaceId;
  private Long packageId;
  private Long packageVersionId;

  public interface Ordering {
    String id = "id";
    String uri = "uri";
    String name = "name";
    String description = "description";
  }
}
