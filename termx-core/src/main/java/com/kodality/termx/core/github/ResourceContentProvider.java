package com.kodality.termx.core.github;

import com.kodality.termx.core.github.GithubService.GithubContent.GithubContentEncoding;
import java.util.List;

public interface ResourceContentProvider {

  String getResourceType();

  String getContentType();

  List<ResourceContent> getContent(String key);

  record ResourceContent(String name, String content, String encoding) {
    public ResourceContent(String name, String content) {
      this(name, content, GithubContentEncoding.utf8);
    }

    public String getName() {
      return this.name;
    }

    public String getContent() {
      return this.content;
    }

    public String getEncoding() {
      return this.encoding;
    }
  }

}
