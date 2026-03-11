package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class VirtualServiceDetail extends Element {
  private Coding channelType;
  private String addressUrl;
  private String addressString;
  private ContactPoint addressContactPoint;
  private ExtendedContactDetail addressExtendedContactDetail;
  private List<String> additionalInfo;
  private Integer maxParticipants;
  private String sessionKey;
}
