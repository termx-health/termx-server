package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Signature extends Element {
  private List<Coding> type;
  private OffsetDateTime when;
  private Reference who;
  private Reference onBehalfOf;
  private String targetFormat;
  private String sigFormat;
  private String data;
}
