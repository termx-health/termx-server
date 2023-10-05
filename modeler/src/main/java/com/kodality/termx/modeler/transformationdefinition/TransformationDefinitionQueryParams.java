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
  private String nameContains;
  private boolean summary;

  private List<Long> permittedIds;

  public interface Ordering {
    String id = "id";
    String name = "name";
    String modified = "modified";
  }
}
