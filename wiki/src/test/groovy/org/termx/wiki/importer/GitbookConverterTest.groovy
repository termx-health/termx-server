package org.termx.wiki.importer

import spock.lang.Specification

class GitbookConverterTest extends Specification {

  def 'strips leading YAML frontmatter'() {
    expect:
    GitbookConverter.convert('---\nicon: briefcase\n---\n\n# Title\n\nBody.') == '\n# Title\n\nBody.'
  }

  def 'card table: drops data-hidden columns (and their null cells) and the data-view attribute'() {
    given:
    def html = '<table data-view="cards"><thead><tr><th></th><th></th><th data-hidden data-type="number"></th></tr></thead>' +
        '<tbody><tr><td><strong>WHO</strong></td><td><em>expert</em></td><td>null</td></tr></tbody></table>'

    when:
    def out = GitbookConverter.convert(html)

    then:
    !out.contains('data-view')
    !out.contains('data-hidden')
    !out.contains('null')           // the hidden number column carried the stray "null"
    out.contains('<strong>WHO</strong>')
    out.contains('<em>expert</em>')
  }

  def 'card table with all-empty headers drops the header row'() {
    when:
    def out = GitbookConverter.convert('<table data-view="cards"><thead><tr><th></th><th></th></tr></thead>' +
        '<tbody><tr><td>a</td><td>b</td></tr></tbody></table>')

    then:
    !out.contains('<thead')
    out.contains('<td>a</td>')
  }

  def 'figure/div-wrapped image is unwrapped to a standalone markdown image'() {
    when:
    def out = GitbookConverter.convert('<div align="left"><figure><img src=".gitbook/assets/a b.png" alt="" width="375"><figcaption></figcaption></figure></div>')

    then: 'no HTML block wrapper remains (markdown inside HTML is not rendered)'
    out == '![](<.gitbook/assets/a b.png>)'
  }

  def 'raw <img> becomes a markdown image (angle-bracketed so spaces stay valid)'() {
    expect:
    GitbookConverter.convert('<img src=".gitbook/assets/a b.png" alt="Me">') == '![Me](<.gitbook/assets/a b.png>)'
    GitbookConverter.convert('<img src="x.png">') == '![](<x.png>)'
  }

  def 'file embed becomes a link to the source'() {
    expect:
    GitbookConverter.convert('{% file src=".gitbook/assets/CV.pdf" %}') == '[CV.pdf](<.gitbook/assets/CV.pdf>)'
  }

  def 'hint becomes a TermX callout blockquote'() {
    when:
    def out = GitbookConverter.convert('{% hint style="warning" %}\nBe careful\n{% endhint %}')

    then:
    out == '> Be careful\n{.is-warning}'
  }

  def 'null and unchanged content pass through'() {
    expect:
    GitbookConverter.convert(null) == null
    GitbookConverter.convert('# Plain\n\nNothing to convert.') == '# Plain\n\nNothing to convert.'
  }
}
