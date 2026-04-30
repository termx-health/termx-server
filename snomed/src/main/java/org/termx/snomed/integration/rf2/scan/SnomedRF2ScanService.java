package org.termx.snomed.integration.rf2.scan;

import com.kodality.commons.util.JsonUtil;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.termx.core.auth.SessionStore;
import org.termx.core.sys.lorque.LorqueProcessService;
import org.termx.core.utils.VirtualThreadExecutor;
import org.termx.snomed.integration.rf2.scan.SnomedRF2ZipParser.ParsedRF2;
import org.termx.snomed.rf2.SnomedImportRequest;
import org.termx.snomed.rf2.SnomedRF2Upload;
import org.termx.snomed.rf2.scan.SnomedRF2Attribute;
import org.termx.snomed.rf2.scan.SnomedRF2Designation;
import org.termx.snomed.rf2.scan.SnomedRF2InvalidatedConcept;
import org.termx.snomed.rf2.scan.SnomedRF2ModifiedConcept;
import org.termx.snomed.rf2.scan.SnomedRF2NewConcept;
import org.termx.snomed.rf2.scan.SnomedRF2ScanEnvelope;
import org.termx.snomed.rf2.scan.SnomedRF2ScanResult;
import org.termx.sys.lorque.LorqueProcess;
import org.termx.sys.lorque.ProcessResult;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SnomedRF2ScanService {
  private static final String PROCESS_NAME = "snomed-rf2-scan";

  private final SnomedRF2ZipParser parser;
  private final SnomedRF2DiffEngine diffEngine;
  private final SnomedRF2UploadCacheService uploadCacheService;
  private final LorqueProcessService lorqueProcessService;

  public LorqueProcess scanRF2(SnomedImportRequest request, byte[] zipBytes, String filename) {
    SnomedRF2Upload upload = uploadCacheService.save(request, filename, zipBytes);
    LorqueProcess process = lorqueProcessService.start(new LorqueProcess().setProcessName(PROCESS_NAME));
    uploadCacheService.attachLorqueId(upload.getId(), process.getId());

    Long uploadId = upload.getId();
    String branchPath = request.getBranchPath();
    String rf2Type = request.getType();

    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        lorqueProcessService.reportProgress(process.getId(), 5, "starting");
        SnomedRF2ScanResult result = buildScan(zipBytes, branchPath, rf2Type, process.getId()).setUploadCacheId(uploadId);
        lorqueProcessService.reportProgress(process.getId(), 90, "rendering report");
        SnomedRF2ScanEnvelope envelope = new SnomedRF2ScanEnvelope().setJson(result).setMarkdown(renderMarkdown(result));
        byte[] payload = JsonUtil.toJson(envelope).getBytes(StandardCharsets.UTF_8);
        lorqueProcessService.reportProgress(process.getId(), 100, "done");
        lorqueProcessService.complete(process.getId(), ProcessResult.binary(payload));
      } catch (Exception e) {
        log.error("SNOMED RF2 dry-run scan failed for upload {}", uploadId, e);
        ProcessResult failure = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(process.getId(), failure);
      }
    }), VirtualThreadExecutor.get());

    return process;
  }

  public SnomedRF2ScanResult buildScan(byte[] zipBytes, String branchPath, String rf2Type) throws java.io.IOException {
    return buildScan(zipBytes, branchPath, rf2Type, null);
  }

  private SnomedRF2ScanResult buildScan(byte[] zipBytes, String branchPath, String rf2Type, Long lorqueId) throws java.io.IOException {
    java.util.function.Consumer<String> phaseReporter = phase -> {
      if (lorqueId == null) {
        return;
      }
      Integer percent = phasePercent(phase);
      if (percent != null) {
        lorqueProcessService.reportProgress(lorqueId, percent, "parsing " + phase);
      }
    };
    ParsedRF2 parsed = parser.parse(zipBytes, phaseReporter);
    if (lorqueId != null) {
      lorqueProcessService.reportProgress(lorqueId, 80, "computing diff");
    }
    return diffEngine.classify(parsed, branchPath, rf2Type);
  }

  private static Integer phasePercent(String phase) {
    return switch (phase) {
      case "concepts" -> 10;
      case "descriptions" -> 30;
      case "text-definitions" -> 45;
      case "relationships" -> 55;
      case "language-refset" -> 70;
      default -> null;
    };
  }

  public String renderMarkdown(SnomedRF2ScanResult result) {
    StringBuilder md = new StringBuilder();
    md.append("# SNOMED RF2 dry-run scan\n\n");
    md.append("- **Branch path**: ").append(nullSafe(result.getBranchPath())).append("\n");
    md.append("- **RF2 type**: ").append(nullSafe(result.getRf2Type())).append("\n");
    md.append("- **Release effectiveTime**: ").append(nullSafe(result.getReleaseEffectiveTime())).append("\n");
    md.append("- **Scanned at**: ").append(nullSafe(result.getScannedAt() == null ? null : result.getScannedAt().toString())).append("\n\n");

    md.append("## Stats\n\n");
    md.append("| Metric | Count |\n");
    md.append("|---|---:|\n");
    SnomedRF2ScanResult.Stats s = Optional.ofNullable(result.getStats()).orElse(new SnomedRF2ScanResult.Stats());
    md.append("| Concepts added | ").append(s.getConceptsAdded()).append(" |\n");
    md.append("| Concepts modified | ").append(s.getConceptsModified()).append(" |\n");
    md.append("| Concepts invalidated | ").append(s.getConceptsInvalidated()).append(" |\n");
    md.append("| Descriptions added | ").append(s.getDescriptionsAdded()).append(" |\n");
    md.append("| Descriptions invalidated | ").append(s.getDescriptionsInvalidated()).append(" |\n");
    md.append("| Relationships added | ").append(s.getRelationshipsAdded()).append(" |\n");
    md.append("| Relationships invalidated | ").append(s.getRelationshipsInvalidated()).append(" |\n\n");

    appendNew(md, result.getNewConcepts());
    appendModified(md, result.getModifiedConcepts());
    appendInvalidated(md, result.getInvalidatedConcepts());

    return md.toString();
  }

  private void appendNew(StringBuilder md, List<SnomedRF2NewConcept> list) {
    md.append("## New concepts (").append(list == null ? 0 : list.size()).append(")\n\n");
    if (list == null || list.isEmpty()) {
      md.append("_None._\n\n");
      return;
    }
    md.append("| Concept | Effective time | Module | Definition status | Designations | Attributes |\n");
    md.append("|---|---|---|---|---|---|\n");
    for (SnomedRF2NewConcept c : list) {
      md.append("| ").append(nullSafe(c.getConceptId())).append(" | ")
          .append(nullSafe(c.getEffectiveTime())).append(" | ")
          .append(nullSafe(c.getModuleId())).append(" | ")
          .append(nullSafe(c.getDefinitionStatusId())).append(" | ")
          .append(formatDesignations(c.getDesignations())).append(" | ")
          .append(formatAttributes(c.getAttributes())).append(" |\n");
    }
    md.append("\n");
  }

  private void appendModified(StringBuilder md, List<SnomedRF2ModifiedConcept> list) {
    md.append("## Modified concepts (").append(list == null ? 0 : list.size()).append(")\n\n");
    if (list == null || list.isEmpty()) {
      md.append("_None._\n\n");
      return;
    }
    md.append("| Concept | + designations | - designations | + attributes | - attributes |\n");
    md.append("|---|---|---|---|---|\n");
    for (SnomedRF2ModifiedConcept c : list) {
      md.append("| ").append(nullSafe(c.getConceptId())).append(" | ")
          .append(formatDesignations(c.getAddedDesignations())).append(" | ")
          .append(formatDesignations(c.getRemovedDesignations())).append(" | ")
          .append(formatAttributes(c.getAddedAttributes())).append(" | ")
          .append(formatAttributes(c.getRemovedAttributes())).append(" |\n");
    }
    md.append("\n");
  }

  private void appendInvalidated(StringBuilder md, List<SnomedRF2InvalidatedConcept> list) {
    md.append("## Invalidated concepts (").append(list == null ? 0 : list.size()).append(")\n\n");
    if (list == null || list.isEmpty()) {
      md.append("_None._\n\n");
      return;
    }
    md.append("| Concept | Effective time | Module | Designations |\n");
    md.append("|---|---|---|---|\n");
    for (SnomedRF2InvalidatedConcept c : list) {
      md.append("| ").append(nullSafe(c.getConceptId())).append(" | ")
          .append(nullSafe(c.getEffectiveTime())).append(" | ")
          .append(nullSafe(c.getModuleId())).append(" | ")
          .append(formatDesignations(c.getDesignations())).append(" |\n");
    }
    md.append("\n");
  }

  private String formatDesignations(List<SnomedRF2Designation> ds) {
    if (ds == null || ds.isEmpty()) {
      return "—";
    }
    return ds.stream().map(d -> {
      String term = nullSafe(d.getTerm()).replace("|", "\\|");
      String type = Optional.ofNullable(d.getType()).orElse("");
      String lang = Optional.ofNullable(d.getLanguage()).orElse("");
      String acc = Optional.ofNullable(d.getAcceptability()).orElse("");
      return term + " (" + type + ", " + lang + ", " + acc + ")";
    }).reduce((a, b) -> a + "<br>" + b).orElse("");
  }

  private String formatAttributes(List<SnomedRF2Attribute> as) {
    if (as == null || as.isEmpty()) {
      return "—";
    }
    return as.stream().map(a -> {
      String type = nullSafe(a.getTypeId());
      String dest = nullSafe(a.getDestinationId());
      Integer group = a.getRelationshipGroup();
      return type + " → " + dest + (group == null ? "" : " [g" + group + "]");
    }).reduce((a, b) -> a + "<br>" + b).orElse("");
  }

  private static String nullSafe(String s) {
    return s == null ? "" : s;
  }
}
