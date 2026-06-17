package org.termx.terminology.fhir

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.termx.TermxIntegTest

import java.net.http.HttpResponse.BodyHandlers

/**
 * Pins that termx-server (Micronaut 4.6 / Netty) gzip-compresses HTTP responses out of the box when
 * a (server-to-server) client sends {@code Accept-Encoding: gzip} — so FHIR clients like the IG
 * Publisher / terminology-explorer that hit {@code :8200} directly (no nginx edge) already get
 * compressed $expand/metadata responses without any server config. Browsers negotiate this
 * automatically; the nginx edge gzip is the complementary lever for the browser hop.
 *
 * <p>java.net.http does NOT auto-negotiate or auto-decode gzip, so a compressed response comes back
 * verbatim: {@code Content-Encoding: gzip} plus a gzip-magic (0x1f 0x8b) body.
 */
@MicronautTest(transactional = true)
class ResponseCompressionIT extends TermxIntegTest {

  def "termx-server gzip-compresses a large response when the client sends Accept-Encoding: gzip"() {
    when: "a gzip-accepting client requests the (large) CapabilityStatement"
    def request = client.builder("/fhir/metadata")
        .header("Accept", "application/fhir+json")
        .header("Accept-Encoding", "gzip")
        .header("Authorization", 'Bearer yupi{"username":"test","privileges":["*.*.*"]}')
        .GET().build()
    def response = client.execute(request, BodyHandlers.ofByteArray())
    def bytes = response.body()
    boolean gzipMagic = bytes.length >= 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b

    then: "the response is gzip-encoded on the wire"
    response.statusCode() == 200
    response.headers().firstValue("content-encoding").orElse("<none>") == "gzip"
    gzipMagic
  }
}
