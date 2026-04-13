package org.termx.ucum.service

import com.kodality.commons.model.QueryResult
import org.termx.core.ts.UcumSearchCacheInvalidator
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.CodeSystemRepository
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemVersion
import org.termx.ts.codesystem.CodeSystemVersionQueryParams
import org.termx.ucum.essence.UcumEssence
import org.termx.ucum.essence.UcumEssenceRepository
import org.termx.ucum.essence.UcumEssenceStorageService
import org.termx.ucum.mapper.UcumVersionMapper
import spock.lang.Specification

class UcumAdministrationServiceTest extends Specification {
  def "import essence stores xml reloads runtime and activates imported terminology version"() {
    given:
    def activated = []
    def savedCodeSystems = []
    def savedVersions = []
    def activatedVersions = []
    def retiredVersions = []
    def essenceStorageService = new UcumEssenceStorageService(new UcumEssenceRepository() {
      @Override
      void cancelActive() {
      }

      @Override
      void save(UcumEssence essence) {
        activated << essence
      }
    })
    def reloadCount = 0
    def ucumService = Stub(UcumService) {
      reload() >> { reloadCount++ }
    }
    def xml = getClass().classLoader.getResourceAsStream("ucum-essence.xml").bytes
    def codeSystemService = new CodeSystemService(null, null, null, null, null) {
      @Override
      Optional<CodeSystem> load(String codeSystem) {
        return Optional.empty()
      }

      @Override
      void save(CodeSystem codeSystem) {
        savedCodeSystems << codeSystem
      }
    }
    def codeSystemVersionService = new CodeSystemVersionService(null, null, null, new CodeSystemRepository() {
      @Override
      CodeSystem load(String codeSystem) {
        return null
      }
    }, new UcumSearchCacheInvalidator() {
      @Override
      void invalidate() {
      }
    }) {
      @Override
      Optional<CodeSystemVersion> load(String codeSystem, String versionCode) {
        return Optional.empty()
      }

      @Override
      QueryResult<CodeSystemVersion> query(CodeSystemVersionQueryParams params) {
        return new QueryResult([new CodeSystemVersion().setVersion("3.0.1")])
      }

      @Override
      void save(CodeSystemVersion version) {
        savedVersions << version
      }

      @Override
      void activate(String codeSystem, String version) {
        activatedVersions << [codeSystem, version]
      }

      @Override
      void retire(String codeSystem, String version) {
        retiredVersions << [codeSystem, version]
      }
    }
    def service = new UcumAdministrationService(
        essenceStorageService,
        ucumService,
        new UcumVersionMapper(),
        codeSystemService,
        codeSystemVersionService
    )

    when:
    def response = service.importEssence(xml)

    then:
    response.version == "2.2"
    activated.size() == 1
    activated[0].version == "2.2"
    activated[0].xml.contains("<root")
    reloadCount == 1
    savedCodeSystems.size() == 1
    savedCodeSystems[0].id == "ucum"
    savedCodeSystems[0].uri == "http://unitsofmeasure.org"
    savedVersions.size() == 1
    savedVersions[0].codeSystem == "ucum"
    savedVersions[0].version == "2.2"
    activatedVersions == [["ucum", "2.2"]]
    retiredVersions == [["ucum", "3.0.1"]]
  }
}
