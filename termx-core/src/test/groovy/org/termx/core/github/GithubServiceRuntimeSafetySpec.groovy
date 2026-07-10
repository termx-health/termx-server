package org.termx.core.github

import spock.lang.Specification

/**
 * Regression guard for the "Spaces -> Edit -> XX100 System error" incident.
 *
 * GithubService.authorizeUser() threw `com.sun.jdi.request.InvalidRequestStateException`
 * (an IDE auto-import mistake - JDI is the Java Debug Interface, module jdk.jdi).
 * That module is NOT shipped in the eclipse-temurin *-jre runtime image the app runs on.
 * When Micronaut instantiates GithubService, the JVM verifier links authorizeUser() and,
 * because of the `throw new InvalidRequestStateException(...)`, tries to load the class to
 * confirm it is a Throwable -> NoClassDefFoundError: com/sun/jdi/request/InvalidRequestStateException
 * -> BeanInstantiationException -> GET /api/spaces/github/providers returns 500 -> the Edit
 * Space page shows "XX100 System error".
 *
 * It compiled fine and passed on a JDK, so a plain instantiate-the-bean test would NOT catch it.
 * This test scans the compiled bytecode's constant pool instead: class references are stored as
 * slash-separated UTF-8 (e.g. "com/sun/jdi/request/InvalidRequestStateException"), so a byte
 * scan reproduces exactly what the runtime verifier would choke on - deterministically, under a JDK.
 */
class GithubServiceRuntimeSafetySpec extends Specification {

  def "GithubService bytecode must not reference JDK-only modules missing from the runtime JRE (jdk.jdi)"() {
    given: "the compiled bytecode of GithubService"
    byte[] bytecode = GithubService.getResourceAsStream("GithubService.class").withCloseable { it.bytes }
    // ISO-8859-1 is a byte-preserving decode: every byte maps 1:1 to a char, so the
    // constant-pool UTF-8 class names survive intact for substring matching.
    String constantPool = new String(bytecode, "ISO-8859-1")

    expect: "no reference to com.sun.jdi - it is absent from the eclipse-temurin:*-jre image"
    !constantPool.contains("com/sun/jdi")
  }
}
