package com.kodality.termserver.ts.project.projectpackage;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Package {
  private Long id;
  private String code;
  private String status;
  private String git;

  private Long projectId;
}