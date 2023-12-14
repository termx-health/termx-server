package com.kodality.termx.modeler.transformationdefinition;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
  @JsonIgnore
  private Object fhirResource;
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

    public interface TransformationDefinitionResourceType {
      String conceptMap = "conceptmap";
      String mapping = "mapping";
      String definition = "definition";
    }

    public interface TransformationDefinitionResourceSource {
      String local = "local";
      String statik = "static";
      String url = "url";
    }
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


