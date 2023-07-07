package com.kodality.termx.snomed.snomed;

import com.kodality.termx.snomed.concept.SnomedTranslation;
import java.util.List;

public abstract class SnomedInterceptor {

  public abstract void afterTranslationSave(String conceptId, SnomedTranslation translation);

  public abstract void afterTranslationsCancel(List<Long> ids);
}
