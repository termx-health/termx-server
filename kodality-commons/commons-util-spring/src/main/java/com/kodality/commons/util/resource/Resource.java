package com.kodality.commons.util.resource;

import lombok.Getter;
import lombok.Setter;

import java.io.UnsupportedEncodingException;

@Getter
@Setter
public class Resource {
  private String name;
  private byte[] content;

  public Resource() {
  }

  public Resource(String name) {
    this.name = name;
  }

  public Resource(String name, byte[] content) {
    this.name = name;
    this.content = content;
  }

  public String getContentString() {
    if (content == null) {
      return null;
    }
    try {
      return new String(content, "UTF8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
