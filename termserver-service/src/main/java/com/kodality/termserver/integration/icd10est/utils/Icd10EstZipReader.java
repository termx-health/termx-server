package com.kodality.termserver.integration.icd10est.utils;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.kodality.termserver.integration.common.utils.XmlMapperUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;


@Slf4j
public class Icd10EstZipReader {
  private static final XmlMapper MAPPER = XmlMapperUtil.getMapper();
  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newFactory();

  public List<Icd10Est> handleZipPack(byte[] bytes) {
    log.info("Unpacking ICD-10 ZIP and parsing XMLs...");
    List<Icd10Est> diagnoses = new ArrayList<>();
    ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(bytes));
    ZipEntry entry;
    try {
      while ((entry = zipIn.getNextEntry()) != null) {
        if (entry.getName().matches("^[A-Z].*xml$")) {
          log.info("Parsing diagnoses file: " + entry.getName());
          diagnoses.add(readClassifierXml(IOUtils.toString(zipIn, Charset.defaultCharset())));
        }
        log.info(entry.getName());
        zipIn.closeEntry();
      }

    } catch (IOException | RuntimeException e) {
      log.error("Error while reading diagnoses pack", e);
      throw new RuntimeException(e);
    }
    return diagnoses;
  }

  public Icd10Est readClassifierXml(String object) {
    XMLStreamReader streamReader = null;
    try {
      streamReader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(object));

      while (streamReader.hasNext()) {
        if (streamReader.hasName() && "classifier".equals(streamReader.getLocalName())) {
          break;
        }
        streamReader.next();
      }
      return MAPPER.readValue(object, Icd10Est.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to read xml", e);
    } finally {
      if (streamReader != null) {
        try {
          streamReader.close();
        } catch (XMLStreamException e) {
          //no worry
        }
      }
    }
  }
}
