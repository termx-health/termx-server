package com.kodality.termx.ts;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ContactDetail {
  private String name;
  private List<Telecom> telecoms;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class Telecom {
    private String system;
    private String value;
    private String use;
  }

}

