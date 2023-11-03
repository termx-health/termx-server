package com.kodality.termx.editionint.icd10;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.core.treminology.Icd10ImportProvider;
import com.kodality.termx.core.ts.CodeSystemImportProvider;
import com.kodality.termx.editionint.icd10.utils.Icd10;
import com.kodality.termx.editionint.icd10.utils.Icd10Mapper;
import com.kodality.termx.editionint.icd10.utils.Icd10ZipReader;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class IntIcd10ImportProvider extends Icd10ImportProvider {
  private final CodeSystemImportProvider importProvider;

  @Override
  public void importIcd10(String system, byte[] file) {
    Icd10 diagnoses = new Icd10ZipReader().handleZipPack(file);
    importProvider.importCodeSystem(Icd10Mapper.toRequest(defaultConfiguration(diagnoses, system), diagnoses));
  }

  private CodeSystemImportConfiguration defaultConfiguration(Icd10 icd10, String uri) {
    return new CodeSystemImportConfiguration().setUri(uri)
        .setPublisher("TBD - External Body")
        .setVersion(icd10.getTitle().getVersion())
        .setValidFrom(icd10.getTitle().getDate())
        .setCodeSystem("icd10")
        .setCodeSystemName(new LocalizedName(Map.of(Language.en, icd10.getTitle().getName())))
        .setCodeSystemDescription(new LocalizedName(Map.of(Language.en, icd10.getTitle().getValue())))
        .setStatus(PublicationStatus.active);
  }
}
