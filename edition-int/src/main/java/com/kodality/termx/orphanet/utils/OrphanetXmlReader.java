package com.kodality.termx.orphanet.utils;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.kodality.termx.utils.XmlMapperUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrphanetXmlReader {
  private static final XmlMapper MAPPER = XmlMapperUtil.getMapper();
  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newFactory();

  public <T> T read(byte[] data, Class<T> clazz) {
    XMLStreamReader streamReader;
    try {
      streamReader = XML_INPUT_FACTORY.createXMLStreamReader(new ByteArrayInputStream(data));
      while (streamReader.hasNext()) {
        if (streamReader.hasName() && clazz.getSimpleName().equals(streamReader.getLocalName())) {
          return MAPPER.readValue(streamReader, clazz);
        }
        streamReader.next();
      }
      return null;
    } catch (IOException | XMLStreamException e) {
      log.error("Error while reading orphanet data", e);
      throw new RuntimeException(e);
    }
  }
}
