package org.termx.wiki

import spock.lang.Specification

class WikiContentNormalizerTest extends Specification {

  def 'R1: attribute-less span autolink-breaker becomes an invisible HTML comment'() {
    expect:
    WikiContentNormalizer.normalize('Draw.<span>io') == 'Draw.<!-- -->io'
    WikiContentNormalizer.normalize('a <span >b') == 'a <!-- -->b'
    and: 'an empty <span></span> pair collapses to a single comment (no orphan close)'
    WikiContentNormalizer.normalize('Draw.<span></span>io') == 'Draw.<!-- -->io'
  }

  def 'R1: a standalone closing </span> is left alone (belongs to an attributed span)'() {
    expect:
    WikiContentNormalizer.normalize('text</span>') == 'text</span>'
  }

  def 'R1: the rewrite breaks auto-linking in both renderers (dot is not rejoined into a link)'() {
    when:
    def out = WikiContentNormalizer.normalize('See Draw.<span>io for details')

    then: 'the comment sits between "Draw." and "io" so markdown-it linkify cannot form a link'
    out == 'See Draw.<!-- -->io for details'
    !out.contains('<span')
  }

  def 'R1: spans carrying attributes are meaningful and left untouched'() {
    expect:
    WikiContentNormalizer.normalize('<span class="x">hi</span>') == '<span class="x">hi</span>'
    WikiContentNormalizer.normalize('<span style="color:red">hi</span>') == '<span style="color:red">hi</span>'
  }

  def 'R2: stray fenced-code language s is aliased to sh'() {
    when:
    def out = WikiContentNormalizer.normalize('```s\necho hi\n```')

    then:
    out == '```sh\necho hi\n```'
  }

  def 'R2: real languages and diagram fences are not touched'() {
    expect:
    WikiContentNormalizer.normalize('```json\n{}\n```') == '```json\n{}\n```'
    WikiContentNormalizer.normalize('```plantuml\n@startuml\n```') == '```plantuml\n@startuml\n```'
    and: 'a closing fence carries no language, so it never matches'
    WikiContentNormalizer.normalize('```\nplain\n```') == '```\nplain\n```'
  }

  def 'unchanged / empty / null content is returned as-is'() {
    expect:
    WikiContentNormalizer.normalize('# Title\n\nJust text.') == '# Title\n\nJust text.'
    WikiContentNormalizer.normalize('') == ''
    WikiContentNormalizer.normalize(null) == null
    !WikiContentNormalizer.needsNormalization('# Title\n\nJust text.')
    WikiContentNormalizer.needsNormalization('Draw.<span>io')
  }
}
