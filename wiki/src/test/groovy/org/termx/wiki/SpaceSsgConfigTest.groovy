package org.termx.wiki

import com.kodality.commons.util.JsonUtil
import org.termx.sys.space.Space
import spock.lang.Specification

class SpaceSsgConfigTest extends Specification {

  def 'ssgConfig shapes the flat space fields into nested mdbook config'() {
    given:
    def space = new Space()
        .setSsgSkin('helex').setSsgThemeAccent('#0aa').setSsgThemeSwitcher(true)
        .setSsgFooterMessage('Guide').setSsgFooterCopyright('(c) 2026')
        .setSsgTxServer('https://dev.termx.org/api/fhir').setSsgSearch(true).setSsgLogo('files/1/logo.png')

    when:
    def cfg = SpaceGithubDataWikiSsgHandler.ssgConfig(space)

    then:
    cfg.theme().skin() == 'helex'
    cfg.theme().accent() == '#0aa'
    cfg.theme().switcher()
    cfg.footer().message() == 'Guide'
    cfg.footer().copyright() == '(c) 2026'
    cfg.txServer() == 'https://dev.termx.org/api/fhir'
    cfg.search()
    cfg.logo() == 'files/1/logo.png'
  }

  def 'ssgConfig is null when nothing is configured, so space.json omits it entirely'() {
    expect:
    SpaceGithubDataWikiSsgHandler.ssgConfig(new Space()) == null
  }

  def 'an unset theme/footer group is dropped while scalars still export'() {
    given:
    def space = new Space().setSsgTxServer('https://x/fhir') // no theme, no footer

    when:
    def cfg = SpaceGithubDataWikiSsgHandler.ssgConfig(space)
    def json = JsonUtil.toJson(cfg)

    then:
    cfg.theme() == null
    cfg.footer() == null
    cfg.txServer() == 'https://x/fhir'
    !json.contains('theme')
    !json.contains('footer')
    json.contains('txServer')
  }
}
