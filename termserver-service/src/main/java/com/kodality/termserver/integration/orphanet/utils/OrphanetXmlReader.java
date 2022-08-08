package com.kodality.termserver.integration.orphanet.utils;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.kodality.termserver.common.utils.XmlMapperUtil;
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

  public OrphanetClassificationList read(byte[] data) {
    XMLStreamReader streamReader;
    try {
      streamReader = XML_INPUT_FACTORY.createXMLStreamReader(new ByteArrayInputStream(data));
      while (streamReader.hasNext()) {
        if (streamReader.hasName() && "ClassificationList".equals(streamReader.getLocalName())) {
          break;
        }
        streamReader.next();
      }
      return MAPPER.readValue(streamReader, OrphanetClassificationList.class);
    } catch (IOException | XMLStreamException e) {
      log.error("Error while reading orphanet data", e);
      throw new RuntimeException(e);
    }
  }
}
