package com.kodality.termserver.observationdefintion;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.LocalizedName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinition extends CodeName {
  private Long id;
  private String code;
  private String version;
  private String publisher;
  private String url;
  private String status;
  private LocalizedName names;
  private LocalizedName alias;
  private LocalizedName definition;
  private LocalizedName keywords;
  private List<ObservationDefinitionCategory> category;
  private String timePrecision;
  private List<String> structure;
  private ObservationDefinitionValue value;
  private List<ObservationDefinitionMember> members;
  private List<ObservationDefinitionComponent> components;
  private ObservationDefinitionProtocol protocol;
  private List<ObservationDefinitionComponent> state;
  private List<ObservationDefinitionInterpretation> interpretations;
  private List<ObservationDefinitionMapping> mappings;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionKeyWord {
    private String lang;
    private String word;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionCategory {
    private String codeSystem;
    private String code;
  }

}
