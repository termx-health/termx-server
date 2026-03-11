package com.kodality.commons.drools;

import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.util.List;
import java.util.Map;

public class DroolsRunner {
  private final KieServices kieServices;

  public DroolsRunner() {
    kieServices = KieServices.Factory.get();
  }

  public void run(byte[] rule, Object fact) {
    run(rule, List.of(fact), null);
  }

  public void run(byte[] rule, Object fact, Map<String, Object> globals) {
    run(rule, List.of(fact), globals);
  }

  public void run(byte[] rule, List<Object> facts) {
    run(rule, facts, null);
  }

  public void run(byte[] rule, List<Object> facts, Map<String, Object> globals) {
    if (rule == null) {
      throw new IllegalArgumentException("rule is empty");
    }
    KieContainer container = ruleContainer(rule);

    KieSession sess = container.newKieSession();
    if (globals != null) {
      globals.keySet().stream().filter(k -> isGlobalDefined(k, sess)).forEach(k -> sess.setGlobal(k, globals.get(k)));
    }
    if (facts != null) {
      facts.forEach(f -> sess.insert(f));
    }
    sess.fireAllRules();
    sess.dispose();
  }

  public List<Message> validate(byte[] rule) {
    return builder(rule).getResults().getMessages();
  }

  public KieContainer ruleContainer(byte[] rule) {
    KieModule kieModule = builder(rule).getKieModule();
    return kieServices.newKieContainer(kieModule.getReleaseId());
  }

  private KieBuilder builder(byte[] rule) {
    KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
    String ruleName = "inmemory";
    kieFileSystem.write("src/main/resources/" + ruleName + ".drl", rule);
    KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
    kieBuilder.buildAll();
    return kieBuilder;
  }

  private boolean isGlobalDefined(String key, KieSession sess) {
    return ((InternalKnowledgeBase) sess.getKieBase()).getGlobals().containsKey(key);
  }

}
