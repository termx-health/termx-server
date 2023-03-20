package com.kodality.termserver.icd10.utils;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.kodality.termserver.utils.XmlMapperUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;


@Slf4j
public class Icd10ZipReader {
  private static final XmlMapper MAPPER = XmlMapperUtil.getMapper();
  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newFactory();

  public Icd10 handleZipPack(byte[] bytes) {
    log.info("Unpacking ICD-10 ZIP and parsing XML");
    ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(bytes));
    ZipEntry entry;
    try {
      while ((entry = zipIn.getNextEntry()) != null) {
        if (entry.getName().matches("^[A-z].*xml$")) {
          log.info("Parsing diagnoses file: " + entry.getName());
          return readClassifierXml(IOUtils.toString(zipIn, Charset.defaultCharset()));
        }
        zipIn.closeEntry();
      }

    } catch (IOException | RuntimeException e) {
      log.error("Error while reading diagnoses pack", e);
      throw new RuntimeException(e);
    }
    return null;
  }

  public Icd10 readClassifierXml(String object) {
    XMLStreamReader streamReader = null;
    try {
      streamReader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(object));
      while (streamReader.hasNext()) {
        if (streamReader.hasName() && "ClaML".equals(streamReader.getLocalName())) {
          break;
        }
        streamReader.next();
      }
      return MAPPER.readValue(prepare(object), Icd10.class);
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

  private String prepare(String object) {
    object = object.replace(", , , ", "").replace(", , ", "");
    String rx = "<Reference.*?>(.*?)</Reference>";

    StringBuilder sb = new StringBuilder();
    Pattern p = Pattern.compile(rx);
    Matcher m = p.matcher(object);
    while (m.find()) {
      m.appendReplacement(sb, "(" + m.group(1) + ")");
    }
    m.appendTail(sb);

    return sb.toString();
  }
}
