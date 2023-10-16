package com.kodality.termx.core.utils;

import io.micronaut.http.multipart.CompletedFileUpload;
import java.io.IOException;

public class FileUtil {
  public static byte[] readBytes(CompletedFileUpload file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
