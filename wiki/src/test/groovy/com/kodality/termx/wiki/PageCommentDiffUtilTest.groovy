package com.kodality.termx.wiki

import com.kodality.termx.wiki.pagecomment.diff.PageCommentDiffUtil
import spock.lang.Specification

class PageCommentDiffUtilTest extends Specification {
  def 'test line removed before the comment'() {
    given:
    def originalText = "" +
        "line 0" +
        "line 1\n" +
        "line 2\n" // is on line 2

    def modifiedText = "" +
        "line 0" +
        "line 2\n" // now on line 1

    when:
    def res = PageCommentDiffUtil.recalculateLineNumber(2, originalText, modifiedText)

    then:
    res == 1
  }

  def 'test line added before the comment'() {
    given:
    def originalText = "" +
        "line 0" +
        "line 1\n" +
        "line 2\n" // is on line 2

    def modifiedText = "" +
        "line 0" +
        "line 1\n" +
        "line 1.1\n" +
        "line 2\n" // now on line 3

    when:
    def res = PageCommentDiffUtil.recalculateLineNumber(2, originalText, modifiedText)

    then:
    res == 3
  }

  def 'test line removed after the comment'() {
    given:
    def originalText = "" +
        "line 0" +
        "line 1\n" + // is on line 1
        "line 2\n" +
        "line 3\n"

    def modifiedText = "" +
        "line 0" +
        "line 1\n" + // still on line 1
        "line 2\n"

    when:
    def res = PageCommentDiffUtil.recalculateLineNumber(1, originalText, modifiedText)

    then:
    res == 1
  }

  def 'test line added after the comment'() {
    given:
    def originalText = "" +
        "line 0" +
        "line 1\n" // is on line 1

    def modifiedText = "" +
        "line 0" +
        "line 1\n" + // still on line 1
        "line 2\n"

    when:
    def res = PageCommentDiffUtil.recalculateLineNumber(1, originalText, modifiedText)

    then:
    res == 1
  }

  def 'test lines removed after/before the comment'() {
    given:
    def originalText = "" +
        "-2\n" +
        "-1\n" +
        "line\n" + // is on line 2
        "1\n" +
        "2"

    def modifiedText = "" +
        "-2\n" +
        "line\n" + // now on line 1
        "2"

    when:
    def res = PageCommentDiffUtil.recalculateLineNumber(2, originalText, modifiedText)

    then:
    res == 1
  }

  def 'test lines added after/before the comment'() {
    given:
    def originalText = "" +
        "-2\n" +
        "-1\n" +
        "line\n" + // is on line 2
        "1\n" +
        "2"

    def modifiedText = "" +
        "-3\n" +
        "-2\n" +
        "-1\n" +
        "line\n" + // now on line 3
        "1\n" +
        "2\n" +
        "3"

    when:
    def res = PageCommentDiffUtil.recalculateLineNumber(2, originalText, modifiedText)

    then:
    res == 3
  }

  def 'test lines added/removed after/before the comment'() {
    given:
    def originalText = "" +
        "-3\n" +
        "-2\n" +
        "-1\n" +
        "line\n" + // now on line 3
        "1\n" +
        "2\n" +
        "3"

    def modifiedText = "" +
        "-1.3\n" +
        "-1.2\n" +
        "-1.1\n" +
        "-1\n" +
        "line\n" + // now on line 4
        "2.5\n" +
        "3\n"

    when:
    def res = PageCommentDiffUtil.recalculateLineNumber(3, originalText, modifiedText)

    then:
    res == 4
  }

  def 'test real world-ish example'() {
    given:
    def originalText = "" +
        "From Wikipedia, the free encyclopedia:\n" +
        "A \"Hello, World!\" program is generally a computer program that ignores any input, and outputs or displays a message similar to \"Hello, World!\".\n" +
        "A small piece of code in most general-purpose programming languages, this program is used to illustrate a language's basic syntax. "

    def modifiedText = "" +
        "A \"Hello, World!\" program is generally a computer program that ignores any input, and outputs.\n" +
        "\n" +
        "\n" +
        "A small piece of code in most general-purpose programming languages, this program is used to illustrate a language's basic syntax.\n" +
        "\n" +
        "\"Hello, World!\" programs are often the first a student learns to write in a given language"

    when:
    def res = PageCommentDiffUtil.recalculateLineNumber(2, originalText, modifiedText)

    then:
    res == 3
  }
}
