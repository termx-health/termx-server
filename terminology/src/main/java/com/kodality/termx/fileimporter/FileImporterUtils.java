package com.kodality.termx.fileimporter;

import io.micronaut.http.multipart.CompletedFileUpload;
import java.io.IOException;

public class FileImporterUtils {
  public static byte[] readBytes(CompletedFileUpload file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
