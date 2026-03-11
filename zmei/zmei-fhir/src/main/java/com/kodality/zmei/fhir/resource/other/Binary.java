package com.kodality.zmei.fhir.resource.other;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.util.Base64;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Binary extends Resource {
  private String contentType;
  private Reference securityContext;
  private String data;

  public Binary() {
    super(ResourceType.binary);
  }

  /**
   * base64
   */
  public void setData(String base64) {
    this.data = base64;
  }

  /**
   * base64
   */
  public String getData() {
    return data;
  }

  @JsonIgnore
  public void setDataBytes(byte[] bytes) {
    setData(new String(Base64.getEncoder().encode(bytes)));
  }

  @JsonIgnore
  public byte[] getDataBytes() {
    return Base64.getDecoder().decode(data);
  }

  @JsonIgnore
  public void setDataString(String data) {
    setDataBytes(data.getBytes());
  }

  @JsonIgnore
  public String getDataString() {
    return new String(getDataBytes());
  }


}
