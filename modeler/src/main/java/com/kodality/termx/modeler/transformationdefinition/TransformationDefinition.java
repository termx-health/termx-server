package com.kodality.termx.modeler.transformationdefinition;

import java.time.OffsetDateTime;
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
  private TransformationDefinitionResource mapping;
  @Valid
  @NotNull
  private List<TransformationDefinitionResource> resources;
  private String testSource;

  private OffsetDateTime createdAt;
  private String createdBy;
  private OffsetDateTime modifiedAt;
  private String modifiedBy;


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
    private String localId;
    private Long resourceServerId;
    private String resourceUrl;
    private String content;
  }
}


