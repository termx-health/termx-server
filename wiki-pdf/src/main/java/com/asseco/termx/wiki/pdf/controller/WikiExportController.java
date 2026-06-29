package com.asseco.termx.wiki.pdf.controller;


import com.asseco.termx.wiki.pdf.Privilege;
import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.styledxmlparser.resolver.font.BasicFontProvider;
import com.jayway.jsonpath.JsonPath;
import org.termx.core.auth.Authorized;
import org.termx.core.github.ResourceContentProvider;
import org.termx.core.sys.space.SpaceService;
import org.termx.core.utils.MatcherUtil;
import org.termx.sys.space.Space;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import org.termx.wiki.page.PageContent;
import org.termx.wiki.page.PageService;
import org.termx.ts.codesystem.*;
import org.termx.ts.valueset.ValueSetSnapshot;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionQueryParams;
import org.termx.wiki.SpaceGithubDataWikiSsgHandler;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Controller("/wiki-export")
@RequiredArgsConstructor
public class WikiExportController {

    private static final String PIPE = "|";
    List<Extension> extensions = List.of(TablesExtension.create());

    private final SpaceGithubDataWikiSsgHandler service;
    private final ConceptService csService;
    private final ValueSetVersionService vsService;
    private final SpaceService spaceService;
    private final PageService pageService;

    @Authorized(Privilege.WIKI_EXPORT_VIEW)
    @Get("/export-html")
    public HttpResponse<?> exportToHtml(@NotNull @QueryValue Long spaceId, @Nullable @QueryValue Long pageId) {
        // Step 1: Convert markdown to HTML
        var htmlContent = createHtmlContent(getMdData(spaceId, pageId));
        return HttpResponse
                .ok(new ByteArrayInputStream(htmlContent.getBytes(StandardCharsets.UTF_8)))
                .contentType(MediaType.APPLICATION_XHTML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename(spaceId, pageId, "html"));
    }

    @Authorized(Privilege.WIKI_EXPORT_VIEW)
    @Get("/export-pdf")
    public HttpResponse<?> exportToPdf(@NotNull @QueryValue Long spaceId, @Nullable @QueryValue Long pageId) {
        return createPdfResponse(getMdData(spaceId, pageId), filename(spaceId, pageId, "pdf"));
    }

    private String getMdData(@NotNull Long spaceId, Long pageId) {
        var mdResources = service.getContent(spaceId);
        var pagesJson = mdResources.stream().filter(p -> p.getName().contains("pages.json"))
                .map(ResourceContentProvider.ResourceContent::getContent).findAny().orElse(null);

        final Map<String, String> nameSlugMap = new LinkedHashMap<>(createNameSlugMap(pagesJson));
        final Set<String> includeSlugs = pageSlugs(pageId); // null => whole space

        // slug -> page content (covers both .md and .html page resources)
        final Pattern slugPattern = Pattern.compile("pages/(.+?)\\.(?:md|html)");
        final Map<String, String> contentBySlug = new LinkedHashMap<>();
        mdResources.stream().filter(p -> p.getName().contains("pages/")).forEach(p -> {
            Matcher m = slugPattern.matcher(p.getName());
            if (m.find()) {
                contentBySlug.putIfAbsent(m.group(1), p.getContent());
            }
        });

        // Page order follows the page tree from pages.json (depth-first, sorted by orderNumber) so the
        // PDF/HTML matches the wiki page menu order, not the flat resource order.
        final List<String> orderedSlugs = pagesJson == null
                ? new ArrayList<>(contentBySlug.keySet())
                : JsonPath.read(pagesJson, "$..contents[*].slug");

        return orderedSlugs.stream()
                .filter(slug -> includeSlugs == null || includeSlugs.contains(slug))
                .filter(contentBySlug::containsKey)
                .map(slug -> {
                    String name = nameSlugMap.get(slug);
                    String heading = name != null ? "# " + name + System.lineSeparator() : "";
                    return this.addCodeSystemData(heading + contentBySlug.get(slug));
                })
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }

    /** Slugs of the requested page's content variants, or null to export the whole space. */
    private Set<String> pageSlugs(Long pageId) {
        if (pageId == null) {
            return null;
        }
        return pageService.load(pageId)
                .map(page -> page.getContents() == null ? List.<PageContent>of() : page.getContents())
                .orElse(List.of()).stream()
                .map(PageContent::getSlug)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** Builds a download filename: Wiki-{spaceCode}[-{pageSlug}].{ext}, sanitized for filesystem safety. */
    private String filename(Long spaceId, Long pageId, String ext) {
        Space space = spaceService.load(spaceId);
        String name = "Wiki-" + (space != null && space.getCode() != null ? space.getCode() : String.valueOf(spaceId));
        if (pageId != null) {
            String slug = pageService.load(pageId)
                    .map(page -> page.getContents() == null ? List.<PageContent>of() : page.getContents())
                    .orElse(List.of()).stream()
                    .map(PageContent::getSlug)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(String.valueOf(pageId));
            name += "-" + slug;
        }
        return name.replaceAll("[^a-zA-Z0-9-_]", "_") + "." + ext;
    }

    private Map<String, String> createNameSlugMap(String pagesJson) {
        final Map<String, String> nameSlugMap = new LinkedHashMap<>();
        // get all name and slug nodes
        List<String> names = JsonPath.read(pagesJson, "$..contents[*].name");
        List<String> slugs = JsonPath.read(pagesJson, "$..contents[*].slug");
        // create nameSlugMap(slug, name)
        for (int i = 0; i < Math.min(names.size(), slugs.size()); i++) {
            nameSlugMap.put(slugs.get(i), names.get(i));
        }
        return nameSlugMap;
    }

    /**
     * return Map of (slugNode, nameNode)
     * @param node
     */
    // Recursive process all  Entry<String, String>
    private static Stream<Map.Entry<String, String>> extractNameSlugStream(JsonNode node) {
        // process all contents nodes
        Stream<AbstractMap.SimpleEntry<String, String>> current = Optional.ofNullable(node.get("contents"))
                .filter(JsonNode::isArray)
                .map(contents -> StreamSupport.stream(contents.spliterator(), false)
                        .map(content -> {
                            JsonNode nameNode = content.get("name");
                            JsonNode slugNode = content.get("slug");
                            if (nameNode != null && slugNode != null) {
                                return new AbstractMap.SimpleEntry<>(slugNode.asText(), nameNode.asText());
                            }
                            return null;
                        })
                        .filter(Objects::nonNull))
                .orElse(Stream.empty());
        // Recursively process all children nodes
        Stream<Map.Entry<String, String>> children = Optional.ofNullable(node.get("children"))
                .filter(JsonNode::isArray)
                .map(childArray -> StreamSupport.stream(childArray.spliterator(), false)
                        .flatMap(WikiExportController::extractNameSlugStream))
                .orElse(Stream.empty());
        return Stream.concat(current, children);
    }
    private String addCodeSystemData(String content) {
        // find tables definition
        // {{csc:EALERGEN|0.0.1; properties=display,code; langs=cs;}}
        // {{vsc:nclp-komponenty-typu-drug|1.0.0; properties=display,code; langs=cs-CZ;}}
        String pattern = "\\{\\{(csc|vsc):([\\w-]+)\\|([^;]+);\\s*properties=([\\w,]+);\\s*langs=([\\w-]+);?\\}\\}";
        var codeList = MatcherUtil.findAllMatches(content, pattern).stream().map(m -> {
            Matcher matcher = Pattern.compile(pattern).matcher(m);
            if (!matcher.find()) {
                return null;
            }
            /*
              type: csc | vsc group 1
              code: EALERGEN | nclp-komponenty-typu-drug  group 2
              version: 0.0.1 | 1.0.0  group 3
              properties: display,code  group 4
              langs: cs | cs-CZ  group 5
             */
            return CodeSystemContent.builder()
                    .type(matcher.group(1))
                    .code(matcher.group(2))
                    .version(matcher.group(3))
                    .properties(matcher.group(4))
                    .langs(matcher.group(5)).build();
        }).filter(Objects::nonNull).toList();
        // load codeSystem and valueSet values
        var replacements  = codeList.stream().map(cs -> {
            StringBuilder sb = new StringBuilder();
            log.info("Processing {}='{}'", cs.type, cs);
            // HEADER
            sb.append(System.lineSeparator()).append(PIPE);
            StringBuilder formatting = new StringBuilder(PIPE);
            Arrays.stream(cs.properties.split(",")).forEach(h -> {
                sb.append(h).append(PIPE);
                formatting.append(":-").append(PIPE);
            });
            sb.append(System.lineSeparator()).append(formatting).append(System.lineSeparator());
            if (cs.type.equals("csc")) {
                addCSValues(sb, cs);
            } else if (cs.type.equals("vsc")) {
                addVSValues(sb, cs);
            } else {
                log.error("Unsupported type={}='{}'", cs.type, cs);
            }
            return sb;
        }).toList();
        // text replacement
        var matcher = Pattern.compile(pattern).matcher(content);
        var result = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            if (index < replacements.size()) {
                String replacement = replacements.get(index).toString();
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                index++;
            } else {
                // If there are fewer replacements than occurrences, we keep the original value
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void addCSValues(StringBuilder sb, CodeSystemContent cs) {
        var params = new ConceptQueryParams();
        params.setLimit(-1);
        params.setCodeSystem(cs.code);
        params.setCodeSystemVersion(cs.version);
        // add CS DATA
        var concepts = csService.query(params).getData();
        concepts.forEach(concept -> {
            sb.append(PIPE);
            Arrays.stream(cs.properties.split(",")).forEach(prop -> {
                if ("code".equals(prop)) {
                    // simple get code from Concept
                    sb.append(concept.getCode()).append(PIPE);
                } else if ("display".equals(prop)) {
                    // get data from designations for valid version number and language
                    var valid = findAnyValidVersion(concept, cs.version);
                    Designation designation = findDesignation(valid, cs.langs, prop);
                    sb.append(designation.getName()).append(PIPE);
                } else {
                    // get data from getPropertyValue for valid version number
                    var valid = findAnyValidVersion(concept, cs.version);
                    var value = valid.getPropertyValue(prop);
                    if (Objects.nonNull(value)) {
                        sb.append(value).append(PIPE);
                    }
                }
            });
            sb.append(System.lineSeparator());
        });
    }

    private void addVSValues(StringBuilder sb, CodeSystemContent cs) {
        var params = new ValueSetVersionQueryParams();
        params.setLimit(-1);
        params.setValueSet(cs.code);
        params.setVersion(cs.version);
        // add VS DATA
        var result = vsService.query(params).getData();
        var concepts = result.stream().findAny().map(ValueSetVersion::getSnapshot)
                .map(ValueSetSnapshot::getExpansion).stream()
                .flatMap(List::stream)
                .toList();
        concepts.forEach(concept -> {
            sb.append(PIPE);
            Arrays.stream(cs.properties.split(",")).forEach(prop -> {
                if ("code".equals(prop)) {
                    // simple get code from Concept
                    sb.append(concept.getConcept().getCode()).append(PIPE);
                } else if ("display".equals(prop)) {
                    Designation designation = concept.getDisplay();
                    sb.append(designation.getName()).append(PIPE);
                } else {
                    // get data from getPropertyValue
                    var value = getPropertyValue(concept, prop);
                    if (Objects.nonNull(value)) {
                        sb.append(value).append(PIPE);
                    }
                }
            });
            sb.append(System.lineSeparator());
        });
    }

    public Optional<Object> getPropertyValue(ValueSetVersionConcept concept, String propertyName){
        return concept.getPropertyValues() == null ? Optional.empty() : concept.getPropertyValues().stream()
                .filter(p -> p.getEntityProperty().equals(propertyName))
                .findFirst().map(EntityPropertyValue::getValue);
    }

    private Designation findDesignation(CodeSystemEntityVersion valid, String langs, String prop) {
        return valid.getDesignations().stream()
                .filter(d -> langs.equals(d.getLanguage()) && prop.equals(d.getDesignationType()))
                .findAny().orElseThrow();
    }

    private CodeSystemEntityVersion findAnyValidVersion(Concept row, String refVersion) {
        var filteredVersions = getFilteredVersions(row, refVersion);
        return filteredVersions.stream().findAny().orElseThrow();
    }

    /**
     * Will return a list of CodeSystemEntityVersion objects where at least one of <br/>
     * the CodeSystemVersionReference inside <br/>
     * the versions list has a version equal to refVersion. <br/>
     */
    private List<CodeSystemEntityVersion> getFilteredVersions(Concept row, String refVersion) {
        return row.getVersions().stream()
                .filter(csEntity -> csEntity.getVersions().stream()
                        .anyMatch(versionRef -> refVersion.equals(versionRef.getVersion())))
                .toList();
    }

    private HttpResponse<?> createPdfResponse(String mdData, String filename) {
        // Step 1: Convert markdown to HTML
        var htmlContent = createHtmlContent(mdData);
        // Step 2: Convert HTML to PDF
        var pdfData = createPdfContent(htmlContent);
        return HttpResponse
                .ok(new ByteArrayInputStream(pdfData))
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
    }

    private String createHtmlContent(String mdData) {
        Parser parser = Parser.builder()
                .extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .build();
        var htmlOut = renderer.render(parser.parse(mdData));
        htmlOut = "<!DOCTYPE html><html><head>" +
                "<meta charset=\"UTF-8\" />" +
                "<style>" + STYLE + "</style>" +
                "</head><body>" + System.lineSeparator() + htmlOut + System.lineSeparator() + "</body></html>";
        return htmlOut;
    }

    /**
     * Wiki export stylesheet (HTML + PDF). Czech-blue accents matching the cs-gov skin.
     * Body font-family is intentionally left to the iText font provider's default so non-ASCII
     * (Czech) glyphs keep rendering; only colours, sizes, spacing and table/code styling are set.
     */
    private static final String STYLE = """
            @page { margin: 2cm; @bottom-right { content: counter(page); color: #888; font-size: 9pt; } }
            body { font-size: 11pt; line-height: 1.5; color: #222; }
            h1 { color: #183C62; font-size: 20pt; margin: 1.2em 0 0.4em; border-bottom: 3px solid #F7B935; padding-bottom: 0.15em; }
            h2 { color: #183C62; font-size: 16pt; margin: 1.1em 0 0.3em; }
            h3 { color: #2A5183; font-size: 13pt; margin: 0.9em 0 0.3em; }
            h4, h5, h6 { color: #333; margin: 0.8em 0 0.3em; }
            p { margin: 0.5em 0; }
            a { color: #183C62; text-decoration: none; }
            ul, ol { margin: 0.4em 0 0.4em 1.4em; }
            li { margin: 0.15em 0; }
            code { background: #F2F4F7; padding: 1px 4px; border-radius: 3px; font-family: monospace; font-size: 0.9em; }
            pre { background: #F2F4F7; padding: 0.8em; border-radius: 4px; }
            pre code { background: none; padding: 0; }
            blockquote { margin: 0.6em 0; padding: 0.2em 1em; border-left: 4px solid #F7B935; background: #FBF8F0; color: #555; }
            table { border-collapse: collapse; width: 100%; margin: 0.8em 0; font-size: 0.95em; }
            th, td { border: 1px solid #C8D3E0; padding: 6px 8px; text-align: left; vertical-align: top; }
            th { background: #183C62; color: #FFFFFF; font-weight: 600; }
            tr:nth-child(even) td { background: #F4F7FB; }
            hr { border: none; border-top: 1px solid #C8D3E0; margin: 1.2em 0; }
            img { max-width: 100%; }
            """;

    private byte[] createPdfContent(String htmlContent) {
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        // Convert HTML to PDF
        ConverterProperties converterProperties = new ConverterProperties();
        FontProvider fontProvider = new BasicFontProvider(false, true, false);
        converterProperties.setFontProvider(fontProvider);
        HtmlConverter.convertToPdf(htmlContent, pdfOutputStream, converterProperties);
        return pdfOutputStream.toByteArray();
    }


    @Builder
    record CodeSystemContent(String type, String code, String version, String properties, String langs) {
    }

}