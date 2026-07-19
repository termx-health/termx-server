package org.termx.wiki.importer

import io.micronaut.http.MediaType
import io.micronaut.http.server.types.files.StreamedFile
import org.termx.core.sys.space.SpaceService
import org.termx.wiki.SpaceGithubDataWikiHandler
import org.termx.wiki.page.Page
import org.termx.wiki.page.PageService
import org.termx.wiki.pageattachment.PageAttachmentService
import org.termx.wiki.pagecontent.PageContentService
import spock.lang.Specification

class WikiGithubImportServiceTest extends Specification {

  // ── asset path resolution ──────────────────────────────────────────────────

  def 'resolveRepoPath: TermX files/<id>/ maps to the attachments/ layout'() {
    expect:
    WikiGithubImportService.resolveRepoPath('files/64/pm.png', 'source/', false, '') == 'source/attachments/64/pm.png'
  }

  def 'resolveRepoPath: a named attachment folder (legacy files/<name>/) also maps to attachments/'() {
    expect:
    WikiGithubImportService.resolveRepoPath('files/wiki/value-set-main.png', 'source/', false, '') == 'source/attachments/wiki/value-set-main.png'
    WikiGithubImportService.resolveRepoPath('files/tutorial/ucum.png', 'source/', false, '') == 'source/attachments/tutorial/ucum.png'
  }

  def 'decodeRef: decodes %XX escapes, keeps a literal + and leaves plain refs unchanged'() {
    expect:
    WikiGithubImportService.decodeRef('files/207/Screenshot%202024-06-11%20at%2014.50.15.png') == 'files/207/Screenshot 2024-06-11 at 14.50.15.png'
    WikiGithubImportService.decodeRef('files/1/a+b.png') == 'files/1/a+b.png'
    WikiGithubImportService.decodeRef('files/1/plain.png') == 'files/1/plain.png'
    WikiGithubImportService.decodeRef('files/1/100%done.png') == 'files/1/100%done.png' // malformed escape -> unchanged
  }

  def 'resolveRepoPath: GitBook .gitbook/assets always resolves to the book root, regardless of ../'() {
    expect:
    WikiGithubImportService.resolveRepoPath('../.gitbook/assets/CV.pdf', 'source/', true, 'general') == 'source/.gitbook/assets/CV.pdf'
    WikiGithubImportService.resolveRepoPath('.gitbook/assets/a b.png', 'source/', true, 'general/sub') == 'source/.gitbook/assets/a b.png'
  }

  def 'resolveRepoPath: a page-relative asset is resolved against the page directory'() {
    expect:
    WikiGithubImportService.resolveRepoPath('img/x.png', 'source/', true, 'general') == 'source/general/img/x.png'
    WikiGithubImportService.resolveRepoPath('../img/x.png', 'source/', true, 'general/sub') == 'source/general/img/x.png'
  }

  def 'normalizePath: resolves ./ and ../ segments'() {
    expect:
    WikiGithubImportService.normalizePath('a/b/../c') == 'a/c'
    WikiGithubImportService.normalizePath('a/./b') == 'a/b'
    WikiGithubImportService.normalizePath('a//b/') == 'a/b'
  }

  // ── file names ──────────────────────────────────────────────────────────────

  def 'safeFileName: strips spaces and special characters, keeps the extension'() {
    expect:
    WikiGithubImportService.safeFileName('Igor @ Valukoja 8 (1).jpeg') == 'Igor-Valukoja-8-1.jpeg'
    WikiGithubImportService.safeFileName('IgorBossenkoCV.pdf') == 'IgorBossenkoCV.pdf'
    WikiGithubImportService.safeFileName('a  b.png') == 'a-b.png'
  }

  def 'attachmentName: takes the basename, dropping query/anchor'() {
    expect:
    WikiGithubImportService.attachmentName('.gitbook/assets/x.png') == 'x.png'
    WikiGithubImportService.attachmentName('files/1/a.png?v=2') == 'a.png'
  }

  // ── which references get imported ────────────────────────────────────────────

  def 'importableAsset: external, anchor and self-references are skipped'() {
    expect:
    !WikiGithubImportService.importableAsset('https://x.io/a.png', true, 1L)
    !WikiGithubImportService.importableAsset('#section', true, 1L)
    !WikiGithubImportService.importableAsset('files/1/a.png', true, 1L) // already this page's attachment
  }

  def 'importableAsset: all local images, but only asset-like links'() {
    expect:
    WikiGithubImportService.importableAsset('.gitbook/assets/a.png', true, 1L)   // image
    WikiGithubImportService.importableAsset('.gitbook/assets/cv.pdf', false, 1L) // asset link
    WikiGithubImportService.importableAsset('files/2/a.png', false, 1L)          // asset link (other page)
    and: 'semantic links are left alone'
    !WikiGithubImportService.importableAsset('page:home', false, 1L)
    !WikiGithubImportService.importableAsset('cs:icd-10', false, 1L)
  }

  // ── hash-based dedup / idempotency ───────────────────────────────────────────

  // storeAttachment only uses PageAttachmentService; the other collaborators aren't touched.
  private WikiGithubImportService serviceWith(PageAttachmentService pas) {
    new WikiGithubImportService(null, null, null, null, pas)
  }

  private static StreamedFile stream(byte[] bytes) {
    new StreamedFile(new ByteArrayInputStream(bytes), MediaType.APPLICATION_OCTET_STREAM_TYPE)
  }

  def 'storeAttachment: uploads when the page has no such attachment'() {
    given:
    def pas = Mock(PageAttachmentService)
    def svc = serviceWith(pas)

    when:
    svc.storeAttachment(1L, 'a.png', [1, 2, 3] as byte[])

    then:
    1 * pas.getAttachments(1L) >> []
    1 * pas.saveAttachments(1L, _)
    0 * pas.deleteAttachmentContent(_, _)
  }

  def 'storeAttachment: skips the upload when an identical file (same hash) is already attached'() {
    given:
    def bytes = [1, 2, 3] as byte[]
    def pas = Mock(PageAttachmentService)
    def svc = serviceWith(pas)

    when:
    svc.storeAttachment(1L, 'a.png', bytes)

    then:
    1 * pas.getAttachments(1L) >> [new Page.PageAttachment(fileName: 'a.png')]
    1 * pas.getAttachmentContent(1L, 'a.png') >> stream(bytes)
    0 * pas.saveAttachments(_, _)      // identical -> reused
    0 * pas.deleteAttachmentContent(_, _)
  }

  def 'storeAttachment: replaces when the name matches but the content differs'() {
    given:
    def pas = Mock(PageAttachmentService)
    def svc = serviceWith(pas)

    when:
    svc.storeAttachment(1L, 'a.png', [1, 2, 3] as byte[])

    then:
    1 * pas.getAttachments(1L) >> [new Page.PageAttachment(fileName: 'a.png')]
    1 * pas.getAttachmentContent(1L, 'a.png') >> stream([9, 9] as byte[])
    1 * pas.deleteAttachmentContent(1L, 'a.png')
    1 * pas.saveAttachments(1L, _)
  }
}
