package com.kodality.termserver.sys.lorque;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class LorqueProcess {
  private Long id;
  private String initiator;
  private String processName;
  private String status;
  private LocalDateTime started;
  private LocalDateTime finished;
  private byte[] result;
  private String resultText;
  private String resultType;
}
