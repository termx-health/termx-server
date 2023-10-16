package com.kodality.termx.auth.auth;

import com.kodality.termx.core.auth.Authorized;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("test")
public class TestController {

  @Get("/a")
  public HttpResponse a() {
    return HttpResponse.ok("\nhohoho a\n\n");
  }

  @Authorized()
  @Get("/b")
  public HttpResponse b() {
    return HttpResponse.ok("\nhohoho b\n\n");
  }
}
