package com.kodality.termx.modeler.transformationdefinition;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransformationDefinition {
  private Long id;
  @NotNull
  private String name;
  @Valid
  @NotNull
  private List<TransformationDefinitionResource> resources;
  @Valid
  @NotNull
  private TransformationDefinitionResource mapping;
  private String testSource;

  @Getter
  @Setter
  public static class TransformationDefinitionResource {
    @NotNull
    private String name;
    private String type;
    @NotNull
    private String source;
    @NotNull
    private TransformationDefinitionResourceReference reference;
  }

  @Getter
  @Setter
  public static class TransformationDefinitionResourceReference {
    private Long structureDefinitionId;
    private String fhirServer;
    private String fhirResource;
    private String content;
  }
}

