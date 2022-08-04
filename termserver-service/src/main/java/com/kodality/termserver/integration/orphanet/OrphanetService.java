package com.kodality.termserver.integration.orphanet;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.orphanet.model.ClassificationList;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class OrphanetService {
  private static final XmlMapper MAPPER = XmlMapperUtil.getMapper();
  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newFactory();

  private final BinaryHttpClient client = new BinaryHttpClient();


  public void importOrpha(String url, ImportConfiguration configuration) {
    try {
      byte[] data = client.GET(url).body();
      process(data);
    } catch (IOException | XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  public void process(byte[] data) throws IOException, XMLStreamException {
    XMLStreamReader streamReader;
    streamReader = XML_INPUT_FACTORY.createXMLStreamReader(new ByteArrayInputStream(data));
    while (streamReader.hasNext()) {
      if (streamReader.hasName() && "ClassificationList".equals(streamReader.getLocalName())) {
        break;
      }
      streamReader.next();
    }
    ClassificationList classificationList = MAPPER.readValue(streamReader, ClassificationList.class);
    log.info("Classification list: {}", classificationList);
    //TODO: Marina will store it somehow
  }
}
