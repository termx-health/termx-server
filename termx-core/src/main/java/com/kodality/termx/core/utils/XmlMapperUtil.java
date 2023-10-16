package com.kodality.termx.core.utils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

public class XmlMapperUtil {

  public static XmlMapper getMapper() {
    JacksonXmlModule xmlModule = new JacksonXmlModule();
    xmlModule.setDefaultUseWrapper(false);

    XmlMapper xmlMapper = new XmlMapper(xmlModule);
    xmlMapper.setSerializationInclusion(Include.NON_NULL);
    xmlMapper.enable(Feature.IGNORE_UNKNOWN);
    xmlMapper.registerModule(new JaxbAnnotationModule());
    xmlMapper.registerModule(new XmlWhitespaceModule());
    xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return xmlMapper;
  }

}
