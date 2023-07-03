package com.kodality.termx.icd10est.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Icd10Est {

  private Chapter chapter;

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Chapter implements Node {

    private String number;
    private String code;

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Obj> object;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> notice;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> include;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> exclude;
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "section")
    private List<Section> children;

    @Override
    public List<Sub> getSub() {
      return null;
    }
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Section implements Node {
    private String code;

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Obj> object;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> notice;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> include;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> exclude;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "subsection")
    private List<SubSection> children;

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Sub> sub;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SubSection implements Node {
    private String code;

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Obj> object;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> notice;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> include;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> exclude;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "item")
    private List<Item> children;

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Sub> sub;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item implements Node {
    private String code;

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Obj> object;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> notice;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Sub> sub;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> include;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> exclude;

    @Override
    public List<Sub> getChildren() {return sub;}
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Sub implements Node {
    private String code;

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Obj> object;

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> include;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IncludeExclude> exclude;
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> notice;


    @Override
    public List<Node> getChildren() {
      return null;
    }

    @Override
    public List<Sub> getSub() {return null;}
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Obj {

    @JacksonXmlProperty(localName = "type", isAttribute = true)
    private String type;

    private Integer hidden; // `nr`

    @JacksonXmlProperty(localName = "name-est")
    private String nameEst;
    @JacksonXmlProperty(localName = "name-lat")
    private String nameLat;
    @JacksonXmlProperty(localName = "name-eng")
    private String nameEng;
    @JacksonXmlProperty(localName = "add_code")
    private String addCode;
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class IncludeExclude {
    private String code;
    private String text;
  }

  public interface Node {
    String getCode();

    List<Obj> getObject();

    List<IncludeExclude> getInclude();

    List<IncludeExclude> getExclude();

    List<String> getNotice();

    List<? extends Node> getChildren();

    List<Sub> getSub();
  }
}

