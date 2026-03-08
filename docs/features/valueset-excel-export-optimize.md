---
name: Optimize Valueset Excel Export
overview: Analysis of performance bottlenecks in valueset Excel export functionality and proposed optimization strategies to improve speed and memory efficiency.
todos:
  - id: analyze-batch-load
    content: Create batch concept loading method in ConceptService to eliminate N+1 queries
    status: completed
  - id: implement-streaming
    content: Replace XSSFWorkbook with SXSSFWorkbook for memory-efficient Excel generation
    status: completed
  - id: optimize-headers
    content: Refactor header composition to single-pass algorithm
    status: completed
  - id: refactor-row-composition
    content: Update composeRow to use pre-loaded concept map instead of individual queries
    status: completed
  - id: apply-to-concept-export
    content: Apply same optimizations to ConceptExportService
    status: completed
isProject: false
---

# Optimize Valueset Excel Export Performance

## Current Implementation Analysis

The valueset Excel export functionality is implemented across these key files:

- `[terminology/src/main/java/com/kodality/termx/terminology/terminology/valueset/expansion/ValueSetExportService.java](terminology/src/main/java/com/kodality/termx/terminology/terminology/valueset/expansion/ValueSetExportService.java)` - Main export logic
- `[termx-core/src/main/java/com/kodality/termx/core/utils/XlsxUtil.java](termx-core/src/main/java/com/kodality/termx/core/utils/XlsxUtil.java)` - Excel file generation
- `[terminology/src/main/java/com/kodality/termx/terminology/terminology/valueset/ValueSetController.java](terminology/src/main/java/com/kodality/termx/terminology/terminology/valueset/ValueSetController.java)` - REST endpoints

## Performance Bottlenecks Identified

### 1. N+1 Query Problem (CRITICAL)

**Location:** `ValueSetExportService.java` lines 119-121

```java
return conceptService.load(pv.asCodingValue().getCodeSystem(), pv.asCodingValue().getCode())
    .map(cv -> ConceptUtil.getDisplay(cv.getLastVersion()...))
```

**Issue:** For each concept with coding property values, a separate database query is executed to fetch display names. With large valuesets containing thousands of concepts, this results in thousands of individual queries.

**Impact:** If a valueset has 10,000 concepts and each has 2 coding properties, this generates 20,000+ database queries, making export extremely slow.

### 2. Memory Inefficiency

**Location:** Multiple areas

**Issues:**

- All concepts loaded into memory at once (line 53)
- All rows generated in memory before Excel creation (line 56)
- `XSSFWorkbook` holds entire workbook in memory during creation
- No streaming or chunking

**Impact:** Large valuesets (50k+ concepts) can cause OutOfMemoryError or trigger garbage collection storms, significantly slowing down the export.

### 3. Inefficient Header Composition

**Location:** `ValueSetExportService.java` lines 68-93 (`composeHeaders` method)

**Issues:**

- Multiple separate passes through all concepts to build headers:
  - Line 72-74: Extract display languages
  - Line 77-80: Extract additional designations
  - Line 82-86: Extract property values
  - Line 88-89: Extract ruleset properties
- Each pass involves expensive stream operations with `flatMap`, `distinct`, and grouping

**Impact:** For 10,000 concepts, this means 40,000+ concept iterations just to build headers.

### 4. Row Composition Complexity

**Location:** `ValueSetExportService.java` lines 95-141 (`composeRow` method)

**Issues:**

- Complex nested stream operations for each row
- Repeated string concatenation and joining operations
- Additional designation and property maps rebuilt for each concept
- N+1 query problem mentioned above occurs here

**Impact:** O(n*m) complexity where n = concepts and m = properties per concept.

### 5. No Batch Processing

**Issues:**

- All work happens in a single async task
- No progress reporting for long-running exports
- No ability to cancel
- Client must poll until entire export completes

**Impact:** Poor user experience for large exports that take minutes.

## Proposed Solutions

### Solution 1: Eliminate N+1 Query Problem (High Priority)

**Approach:** Batch load all required concept displays upfront

**Changes needed:**

1. Before row composition, collect all unique (codeSystem, code) pairs for coding properties
2. Create a batch load method in `ConceptService` to fetch all concepts in a single query (or small number of batched queries)
3. Store results in a Map for O(1) lookup during row composition
4. Modify line 119-121 to use the pre-loaded map instead of calling `conceptService.load()`

**Expected improvement:** Reduce thousands of queries to 1-10 queries. Estimate 10-50x speedup for large datasets.

### Solution 2: Implement Streaming Excel Generation

**Approach:** Use Apache POI's `SXSSFWorkbook` instead of `XSSFWorkbook`

**Changes needed:**

1. Modify `XlsxUtil.composeXlsx()` to accept an optional parameter for streaming mode
2. Use `SXSSFWorkbook` which keeps only a configurable window of rows in memory
3. Process and write rows incrementally instead of building entire row list first
4. Modify `ValueSetExportService.composeResult()` to stream rows directly to Excel writer

**Expected improvement:** Memory usage reduced from O(n) to O(1), enabling exports of 100k+ concepts. Prevents OutOfMemoryErrors.

### Solution 3: Optimize Header Composition

**Approach:** Single-pass header collection with early exit optimization

**Changes needed:**

1. Combine all header collection logic into one pass through concepts
2. Use a Set for deduplication during collection rather than distinct() at the end
3. Consider limiting scan to first N concepts if headers are stable (e.g., first 1000)
4. Cache headers per valueset version if they don't change

**Expected improvement:** Reduce header composition time by 75%, especially noticeable with 10k+ concepts.

### Solution 4: Add Batch Processing with Progress Tracking

**Approach:** Process export in chunks with status updates

**Changes needed:**

1. Modify `LorqueProcess` to support progress percentage
2. Process concepts in batches (e.g., 1000 at a time)
3. Update progress after each batch
4. Allow frontend to show progress bar

**Expected improvement:** Better UX, ability to cancel, clearer feedback for long-running exports.

### Solution 5: Add Caching Layer

**Approach:** Cache frequently exported valuesets

**Changes needed:**

1. Add cache key based on (valueSet, version, format)
2. Check cache before starting export
3. Invalidate on valueset version changes
4. Set TTL (e.g., 1 hour) for cached exports

**Expected improvement:** Instant response for repeated exports of same valueset.

## Implementation Priority

**Phase 1 (Critical - Maximum Impact):**

- Solution 1: Eliminate N+1 queries
- Solution 2: Streaming Excel generation

**Phase 2 (Important):**

- Solution 3: Optimize header composition
- Apply same optimizations to `ConceptExportService.java` (has identical issues)

**Phase 3 (Nice to have):**

- Solution 4: Progress tracking
- Solution 5: Caching

## Similar Issues in ConceptExportService

The file `[terminology/src/main/java/com/kodality/termx/terminology/terminology/codesystem/concept/ConceptExportService.java](terminology/src/main/java/com/kodality/termx/terminology/terminology/codesystem/concept/ConceptExportService.java)` has similar patterns and would benefit from the same optimizations.

## Testing Recommendations

After implementing fixes:

1. Test with small valueset (100 concepts) - verify correctness
2. Test with medium valueset (5,000 concepts) - measure speedup
3. Test with large valueset (50,000 concepts) - verify memory usage
4. Monitor database query count using query logging
5. Profile with JProfiler or similar to confirm improvements

## Estimated Impact

Based on the issues identified:

- Current: 10k concept export might take 5-10 minutes with thousands of DB queries
- After Phase 1: Same export should take 10-30 seconds with <10 DB queries
- Memory: Should handle 100k+ concepts without OutOfMemoryError

