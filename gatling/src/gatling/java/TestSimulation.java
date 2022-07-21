import io.gatling.javaapi.core.ScenarioBuilder;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class TestSimulation extends BaseSimulation {
  @Override
  protected ScenarioBuilder getScenario() {
    return scenario("scenario1")
        .exec(req("load mapsets", "/ts/map-sets?limit=" + searchLimit + "&offset=0&lang=en&versionsDecorated=true"))
        .exec(req("load association types", "/ts/association-types?limit=" + searchLimit + "&offset=0&lang=en&decorated=true"))
        .exec(req("load naming systems", "/ts/naming-systems?limit=" + searchLimit + "&offset=0&lang=en&decorated=true"))
        .exec(session -> {
              String id = UUID.randomUUID().toString();
              return session.set("vs_id", id);
            }
        )
        .exec(http("create valueset")
            .post(terminologyApi + "/ts/value-sets")
            .body(StringBody(s -> """
                  {
                      "id": "random_id",
                      "uri": "test_random_id",
                      "names": {
                        "en": ""
                      }
                    }
                """.replaceAll("random_id", s.getString("vs_id"))))
            .check(status().is(201)))
        .exec(req("load created valueset", "/ts/value-sets/#{vs_id}"))
        .exec(http("create valueset version")
            .post(s -> terminologyApi + "/ts/value-sets/" + s.getString("vs_id") + "/versions")
            .body(StringBody(s -> """
                  {
                    "version": "first_version",
                    "releaseDate": "2022-07-21T12:26:46.860Z",
                    "supportedLanguages": [
                      "cs",
                      "de",
                      "de-CH"
                    ],
                    "description": "desc",
                    "source": "src",
                    "status": "draft",
                    "ruleSet": {
                      "rules": []
                    }
                  }
                  
                """))
            .check(status().is(201)))
        .exec(session -> {
              String id = UUID.randomUUID().toString();
              return session.set("cs_id", id);
            }
        )
        .exec(http("create codesystem")
            .post(terminologyApi + "/ts/code-systems")
            .body(StringBody(s -> """
                  {
                       "names": {
                         "en": "ss"
                       },
                       "id": "random_id",
                       "content": "complete",
                       "caseSensitive": "ci",
                       "description": "desc",
                       "uri": "random_id_uri",
                       "contacts": [
                         {
                           "name": "contact1",
                           "telecoms": [
                             {
                               "system": "sms",
                               "value": "1234",
                               "use": "home"
                             }
                           ]
                         },
                         {
                           "name": "contact2",
                           "telecoms": [
                             {
                               "system": "email",
                               "value": "xxxas@dsadas.das",
                               "use": "work"
                             }
                           ]
                         }
                       ]
                     }
                     
                """.replaceAll("random_id", s.getString("cs_id"))))
            .check(status().is(201)))
        .exec(req("load created codesystem", "/ts/code-systems/#{cs_id}"))
        .exec(req("load codesystems", "/ts/code-systems?limit=" + searchLimit + "&offset=0&lang=en&versionsDecorated=true"))
        .exec(req("load valuesets", "/ts/value-sets?limit=" + searchLimit + "&offset=0&lang=en&decorated=true"))
        ;
  }

}
