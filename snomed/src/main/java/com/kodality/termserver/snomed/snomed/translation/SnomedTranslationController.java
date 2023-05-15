package com.kodality.termserver.snomed.snomed.translation;


import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.snomed.concept.SnomedTranslation;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/snomed-translations")
@RequiredArgsConstructor
public class SnomedTranslationController {
  private final SnomedTranslationService translationService;

  @Authorized("snomed-ct.CodeSystem.view")
  @Get("/{conceptId}")
  public List<SnomedTranslation> loadTranslations(@Parameter String conceptId) {
    return translationService.load(conceptId);
  }

  @Authorized({"snomed-ct.CodeSystem.edit"})
  @Post("/{conceptId}")
  public HttpResponse<?> saveTranslations(@Parameter String conceptId, @Body List<SnomedTranslation> translations) {
    translationService.save(conceptId, translations);
    return HttpResponse.ok();
  }
}
