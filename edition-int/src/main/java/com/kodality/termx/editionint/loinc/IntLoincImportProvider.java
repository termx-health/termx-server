package com.kodality.termx.editionint.loinc;

import com.kodality.termx.core.treminology.LoincImportProvider;
import com.kodality.termx.editionint.loinc.utils.LoincImportRequest;
import com.kodality.termx.editionint.loinc.utils.LoincZipReader;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@RequiredArgsConstructor
public class IntLoincImportProvider extends LoincImportProvider {
  private final LoincService service;

  @Override
  public void importLoinc(String system, byte[] file) {
    List<Pair<String, byte[]>> files = new LoincZipReader().handleZipPack(file);
    service.importLoinc(Map.of("request", new LoincImportRequest().setVersion("1"), "files", files));
  }
}
