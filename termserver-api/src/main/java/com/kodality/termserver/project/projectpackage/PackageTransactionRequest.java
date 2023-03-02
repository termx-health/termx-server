package com.kodality.termserver.project.projectpackage;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PackageTransactionRequest {
  private Package pack;
  private PackageVersion version;
}
