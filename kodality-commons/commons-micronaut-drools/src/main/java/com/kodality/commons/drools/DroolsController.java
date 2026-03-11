package com.kodality.commons.drools;

import com.kodality.commons.util.JsonUtil;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.kie.api.builder.Message;


@RequiredArgsConstructor()
@Controller("internal/drools")
public class DroolsController {

  private final DroolsConfiguration droolsConfiguration;
  private DroolsRunner droolsRunner;

  @PostConstruct
  public void init() {
    droolsRunner = new DroolsRunner();
  }

  @Post("/execute")
  public DroolsExecuteResponse execute(@Body DroolsExecuteRequest request) {
    Class<?> objectClass = droolsConfiguration.getClassMappings().get(request.getRuleContext());
    Object fact = JsonUtil.fromJson(request.getInput(), objectClass);
    droolsRunner.run(request.getRule().getBytes(), fact);

    DroolsExecuteResponse response = new DroolsExecuteResponse();
    response.setOutput(JsonUtil.toJson(fact));
    return response;
  }

  @Post("/validate")
  public List<Message> validate(@Body DroolsValidateRequest request) {
    return droolsRunner.validate(request.getRule().getBytes());
  }
}
