package com.kodality.termx.sys.space;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.sys.spacepackage.Package;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class Space {
  private Long id;
  private String code;
  private LocalizedName names;
  private boolean active;
  private boolean shared;
  private Object acl;
  private List<String> terminologyServers;

  private List<Package> packages;
}
