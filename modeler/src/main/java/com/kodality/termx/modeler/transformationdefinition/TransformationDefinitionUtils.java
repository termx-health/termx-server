package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResourceReference;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.StructureMap;

import java.io.IOException;
import java.util.List;

@UtilityClass
public class TransformationDefinitionUtils {

  public static TransformationDefinition createTransformationDefinitionFromResource(StructureMap source) throws IOException {
    final TransformationDefinition td = new TransformationDefinition();

    // resources (id/refs)
    addResources(td, source.getStructure());

    // mapping (main data 2Mb)
    String json = new JsonParser().setOutputStyle(IParser.OutputStyle.PRETTY).composeString(source);
    td.setMapping(
        new TransformationDefinitionResource()
            .setName("main")
            .setType("mapping")
            .setSource("static")
            .setReference(
                new TransformationDefinitionResourceReference()
                    .setContent(json)
            )
    );
    return td;
  }

  private static void addResources(TransformationDefinition td, List<StructureMap.StructureMapStructureComponent> structure) {
    td.setResources(structure.stream().map(item ->
        new TransformationDefinitionResource()
            .setName(item.getAlias())
            .setType("definition")
            .setSource("url")
            .setReference(new TransformationDefinitionResourceReference()
                .setResourceUrl(item.getUrl()))
    ).toList());
  }
}
