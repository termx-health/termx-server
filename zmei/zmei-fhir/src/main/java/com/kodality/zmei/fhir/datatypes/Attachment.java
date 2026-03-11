package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Attachment extends Element {
  private String contentType;
  private String language;
  private String data;
  private String url;
  private String size;
  private String hash;
  private String title;
  private OffsetDateTime creation;
}
