package com.kodality.termx.editionint.icd10.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import com.kodality.termx.core.utils.SimpleDateDeserializer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
@JacksonXmlRootElement(localName = "ClaML")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Icd10 {
  @JacksonXmlProperty(localName = "Title")
  @JacksonXmlElementWrapper(useWrapping = false)
  private Title title;

  @JacksonXmlProperty(localName = "Class")
  @JacksonXmlElementWrapper(useWrapping = false)
  private List<Class> classes;

  public void setTitle(Title title) {
    this.title = title;
  }

  public void setClasses(List<Class> classes) {
    if (this.classes == null) {
      this.classes = new ArrayList<>();
    }
    this.classes.addAll(classes);
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Title {
    @JacksonXmlProperty(localName = "date", isAttribute = true)
    @JsonDeserialize(using = SimpleDateDeserializer.class)
    private LocalDate date;

    @JacksonXmlProperty(localName = "name", isAttribute = true)
    private String name;

    @JacksonXmlProperty(localName = "version", isAttribute = true)
    private String version;

    @JacksonXmlText
    private String value;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Class {
    @JacksonXmlProperty(localName = "SuperClass")
    private SuperClass superClass;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Rubric")
    private List<Rubric> rubrics;

    @JacksonXmlProperty(localName = "code", isAttribute = true)
    private String code;

    @JacksonXmlProperty(localName = "kind", isAttribute = true)
    private String kind;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SuperClass {
    @JacksonXmlProperty(localName = "code", isAttribute = true)
    private String code;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Rubric {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Label")
    private Label label;

    @JacksonXmlProperty(localName = "kind", isAttribute = true)
    private String kind;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Label {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Fragment")
    private List<Fragment> fragment;
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Para")
    private List<Para> para;
    @JacksonXmlText
    private String value;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Fragment {
    @JacksonXmlProperty(localName = "type", isAttribute = true)
    private String type;
    @JacksonXmlText
    private String value;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Para {
    @JacksonXmlProperty(localName = "type", isAttribute = true)
    private String type;
    @JacksonXmlText
    private String value;
  }
}
