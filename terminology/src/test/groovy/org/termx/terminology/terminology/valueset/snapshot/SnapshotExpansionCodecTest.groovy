package org.termx.terminology.terminology.valueset.snapshot

import com.kodality.commons.util.JsonUtil
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue
import spock.lang.Specification

class SnapshotExpansionCodecTest extends Specification {

  def "gzip round-trips an expansion and actually compresses it"() {
    given:
    def expansion = (1..200).collect {
      new ValueSetVersionConcept().setId(it as Long)
          .setConcept(new ValueSetVersionConceptValue().setCode("code-" + it).setCodeSystem("cs"))
    }

    when:
    def gz = SnapshotExpansionCodec.encode(expansion)
    def back = SnapshotExpansionCodec.decode(gz)

    then: "content survives the round trip"
    back.size() == 200
    back*.concept.code == expansion*.concept.code

    and: "the gzip is materially smaller than the raw JSON"
    gz.length < JsonUtil.toJson(expansion).getBytes("UTF-8").length
  }

  def "null and empty expansions encode to an empty list"() {
    expect:
    SnapshotExpansionCodec.decode(SnapshotExpansionCodec.encode(null)) == []
    SnapshotExpansionCodec.decode(SnapshotExpansionCodec.encode([])) == []
  }
}
