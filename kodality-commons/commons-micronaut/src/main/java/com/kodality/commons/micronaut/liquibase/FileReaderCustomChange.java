package com.kodality.commons.micronaut.liquibase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.resource.Resource;
import liquibase.resource.ResourceAccessor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public abstract class FileReaderCustomChange implements CustomTaskChange {
  protected List<String> files;
  protected String dir;
  protected ResourceAccessor resourceAccessor;

  protected abstract void handleFile(String name, byte[] content);

  public void setDir(String dir) {
    this.dir = dir;
  }

  public void setFiles(String files) {
    this.files = Arrays.asList(StringUtils.split(files));
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
    this.resourceAccessor = resourceAccessor;
  }

  @Override
  public String getConfirmationMessage() {
    return null;
  }

  @Override
  public void setUp() {
  }

  @Override
  public ValidationErrors validate(Database database) {
    return null;
  }

  @Override
  public void execute(Database database) {
    try {
      if (dir != null) {
        for (Resource resource : resourceAccessor.search(dir, true)) {
          executeFile(resource);
        }
      }

      if (files != null) {
        for (String file : files) {
          executeFile(resourceAccessor.get(file));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void executeFile(Resource resource) throws IOException {
    InputStream is = resource.openInputStream();
    byte[] content = IOUtils.toByteArray(is);
    String name = StringUtils.substringAfterLast(resource.getPath(), "/");
    handleFile(name, content);
  }

  protected String asString(byte[] content) {
    if (content == null) {
      return null;
    }
    return new String(content, StandardCharsets.UTF_8);
  }
}
