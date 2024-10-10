package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResourceReference;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class TransformationDefinitionUtils {

  public static TransformationDefinition createTransformationDefinitionFromJson(String json) {
    final Map<String, Object> content = JsonUtil.toMap(json);

    final TransformationDefinition result = new TransformationDefinition();
    result.setMapping(
        new TransformationDefinitionResource()
            .setReference(
                new TransformationDefinitionResourceReference()
                    .setContent(json)
            )
    );

    return result;
  }
}
