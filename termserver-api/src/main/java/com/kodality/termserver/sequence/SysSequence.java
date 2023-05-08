package com.kodality.termserver.sequence;

import java.time.LocalDate;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SysSequence {
  private Long id;
  @NotNull
  private String code;
  private String description;
  private String pattern;
  private String restart;
  private String startFrom;

  // decorated
  private List<SysSequenceLuv> luvs;

  @Getter
  @Setter
  public static class SysSequenceLuv {
    private LocalDate period;
    private String luv;
  }
}
