package com.kodality.termx.core.fhir.provenance;

import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.security.Provenance.ProvenanceAgent;
import java.util.ArrayList;
import java.util.List;

public class ProvenanceFhirMapper extends BaseFhirMapper {

  public static String toFhirJson(Provenance p) {
    return FhirMapper.toJson(toFhir(p));
  }

  public static com.kodality.zmei.fhir.resource.security.Provenance toFhir(Provenance p) {
    com.kodality.zmei.fhir.resource.security.Provenance fhir = new com.kodality.zmei.fhir.resource.security.Provenance();
    fhir.setId(p.getId().toString());
    fhir.setTarget(new ArrayList<>(2));
    fhir.getTarget().add(new Reference(p.getTarget().getType(), p.getTarget().getId()));
    if (p.getContext() != null) {
      fhir.getTarget().addAll(p.getContext().stream().filter(c -> c.getRole().equals("part-of"))
          .map(c -> new Reference(c.getEntity().getType(), c.getEntity().getId())).toList());
    }
    fhir.setOccurredDateTime(p.getDate());
    fhir.setActivity(CodeableConcept.fromCodes(p.getActivity()));
    fhir.setAgent(List.of(new ProvenanceAgent().setType(CodeableConcept.fromCodes("author")).setWho(new Reference().setDisplay(p.getAuthor().getId()))));
    return fhir;
  }

}
