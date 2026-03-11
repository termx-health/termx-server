package com.kodality.commons.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Supplier;

public class MultipartBodyPublisher {

  private final List<Part> parts = new ArrayList<>();
  private final String boundary = UUID.randomUUID().toString();

  public HttpRequest.BodyPublisher build() {
    if (parts.size() == 0) {
      throw new IllegalStateException("Must have at least one part to build multipart message.");
    }
    addFinalBoundaryPart();
    return HttpRequest.BodyPublishers.ofByteArrays(PartsIterator::new);
  }

  public String getBoundary() {
    return boundary;
  }

  public MultipartBodyPublisher addPart(String name, String value) {
    Part part = new Part();
    part.type = Part.TYPE.STRING;
    part.name = name;
    part.value = value;
    parts.add(part);
    return this;
  }

  public MultipartBodyPublisher addPart(String name, Supplier<InputStream> value, String filename, String contentType) {
    Part part = new Part();
    part.type = Part.TYPE.STREAM;
    part.name = name;
    part.stream = value;
    part.filename = filename;
    part.contentType = contentType;
    parts.add(part);
    return this;
  }

  private void addFinalBoundaryPart() {
    Part part = new Part();
    part.type = Part.TYPE.FINAL_BOUNDARY;
    part.value = "--" + boundary + "--";
    parts.add(part);
  }

  static class Part {
    public enum TYPE {
      STRING, STREAM, FINAL_BOUNDARY
    }

    TYPE type;
    String name;
    String value;
    Supplier<InputStream> stream;
    String filename;
    String contentType;
  }

  class PartsIterator implements Iterator<byte[]> {

    private final Iterator<Part> iter;
    private InputStream currentFileInput;

    private boolean done;
    private byte[] next;

    PartsIterator() {
      iter = parts.iterator();
    }

    @Override
    public boolean hasNext() {
      if (done) {
        return false;
      }
      if (next != null) {
        return true;
      }
      try {
        next = computeNext();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      if (next == null) {
        done = true;
        return false;
      }
      return true;
    }

    @Override
    public byte[] next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      byte[] res = next;
      next = null;
      return res;
    }

    private byte[] computeNext() throws IOException {
      if (currentFileInput == null) {
        if (!iter.hasNext()) {
          return null;
        }
        Part nextPart = iter.next();
        if (Part.TYPE.STRING.equals(nextPart.type)) {
          String part =
              "--" + boundary + "\r\n" +
                  "Content-Disposition: form-data; name=" + nextPart.name + "\r\n" +
                  "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                  nextPart.value + "\r\n";
          return part.getBytes(StandardCharsets.UTF_8);
        }
        if (Part.TYPE.FINAL_BOUNDARY.equals(nextPart.type)) {
          return nextPart.value.getBytes(StandardCharsets.UTF_8);
        }

        currentFileInput = nextPart.stream.get();

        String partHeader =
            "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=" + nextPart.name + "; filename=" + nextPart.filename + "\r\n" +
                "Content-Type: " + nextPart.contentType + "\r\n\r\n";
        return partHeader.getBytes(StandardCharsets.UTF_8);
      } else {
        byte[] buf = new byte[8192];
        int r = currentFileInput.read(buf);
        if (r > 0) {
          byte[] actualBytes = new byte[r];
          System.arraycopy(buf, 0, actualBytes, 0, r);
          return actualBytes;
        } else {
          currentFileInput.close();
          currentFileInput = null;
          return "\r\n".getBytes(StandardCharsets.UTF_8);
        }
      }
    }
  }
}
