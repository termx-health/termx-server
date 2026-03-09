package org.termx.editionint.icd10;

import com.kodality.commons.model.LocalizedName;
import org.termx.core.treminology.Icd10ImportProvider;
import org.termx.core.ts.CodeSystemImportProvider;
import org.termx.editionint.icd10.utils.Icd10;
import org.termx.editionint.icd10.utils.Icd10Mapper;
import org.termx.editionint.icd10.utils.Icd10ZipReader;
import org.termx.ts.Language;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystemImportConfiguration;
import java.util.Map;
import jakarta.inject.Singleton;
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
