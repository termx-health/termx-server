package com.kodality.termserver.observationdefintion;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionProtocol {
  private Long id;
  private ObservationDefinitionProtocolValue device;
  private ObservationDefinitionProtocolValue method;
  private ObservationDefinitionProtocolValue measurementLocation;
  private ObservationDefinitionProtocolValue specimen;
  private ObservationDefinitionProtocolValue position;
  private ObservationDefinitionProtocolValue dataCollectionCircumstances;
  private List<ObservationDefinitionComponent> components;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionProtocolValue {
    private String usage;
    private List<String> values;
    private String valueSet;
  }
}
