package com.kodality.termserver.taskflow;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthUser {
  private String id;
  private String name;
  private String firstName;
  private String lastName;
  private String username;
  private String email;
}
