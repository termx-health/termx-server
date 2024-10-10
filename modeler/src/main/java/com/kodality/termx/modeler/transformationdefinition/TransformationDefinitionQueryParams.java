package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TransformationDefinitionQueryParams extends QueryParams {
  private String ids;
  private String name;
  private String nameContains;

  // queries using transformation_definition.fhir_resource
  private Boolean fhirExists;
  private String fhirIds;
  private String fhirUrls;
  private String fhirDescriptionContains;
  private String fhirTitleContains;
  private String fhirStatuses;

  private boolean summary;
  private List<Long> permittedIds;

  private Long spaceId;
  private Long packageId;
  private Long packageVersionId;

  public interface Ordering {
    String id = "id";
    String name = "name";
    String modified = "modified";
  }
}
