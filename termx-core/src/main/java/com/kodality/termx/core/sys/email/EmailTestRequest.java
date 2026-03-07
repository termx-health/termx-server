package com.kodality.termx.core.sys.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EmailTestRequest {
  @NotBlank
  @Email
  private String recipient;
  
  @NotBlank
  private String subject;
  
  @NotBlank
  private String body;
  
  private boolean html = false;
}
