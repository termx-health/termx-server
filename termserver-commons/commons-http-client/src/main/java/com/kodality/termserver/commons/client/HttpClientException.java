package com.kodality.termserver.commons.client;

public class HttpClientException extends RuntimeException {

  public HttpClientException(Throwable e) {
    super(e);
  }

  public HttpClientException(String message, Throwable e) {
    super(message, e);
  }

}
