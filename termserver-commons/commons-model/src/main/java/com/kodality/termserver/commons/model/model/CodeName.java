package com.kodality.termserver.commons.model.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CodeName implements CodeNameable {
  private Long id;
  private String code;
  private LocalizedName names = new LocalizedName();

  public CodeName(String code) {
    this.code = code;
  }

  public CodeName(Long id) {
    this.id = id;
  }

  public static CodeName of(String code, String name, String lang) {
    return new CodeName(code).setNames(new LocalizedName().add(lang, name));
  }

  public static String extractCode(CodeName codeName) {
    return codeName == null ? null : codeName.getCode();
  }

}
