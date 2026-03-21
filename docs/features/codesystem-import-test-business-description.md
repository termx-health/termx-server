# CodeSystem Import Tests - Business Description

## Overview

This document describes the business logic and validation scenarios covered by the comprehensive import tests for CodeSystem concept import functionality. It includes file parsing/processor tests, domain mapper tests, **dry-run (“validate data only”) service tests**, and related coverage notes.

## Test Suite: CodeSystemFileImportProcessorTest

### Test 1: CSV Import with New Format Columns (Single Values)

**Business Purpose:**
Validates that CSV files can be imported with the new column format when there's only one value per property (order suffix omitted).

**Key Validations:**
- CSV file parsing works correctly
- Columns without order suffix default to order 1
- Designations are correctly parsed using `type#language` format
- Simple properties (string, decimal) are correctly parsed
- Coding properties are correctly paired (code and system columns)
- Empty cells are properly skipped

**Business Scenario:**
A user exports a CodeSystem with single values for each property. The export omits the order suffix (e.g., `itemWeight` instead of `itemWeight##1`). When importing this file back, the system must correctly recognize these columns and import the data.

**Expected Behavior:**
- All properties are imported with correct values
- Empty cells don't create empty property values
- Coding properties are correctly paired and stored

---

### Test 2: CSV Import with New Format Columns (Multiple Values with Order Suffix)

**Business Purpose:**
Validates that CSV files can be imported with multiple values per property using the order suffix format.

**Key Validations:**
- Multiple values for the same property are correctly ordered
- Order suffixes (`##1`, `##2`) are correctly parsed
- Multiple designations of the same type/language are correctly ordered
- Multiple coding values are correctly paired by order
- Incomplete coding pairs (missing code or system) are handled correctly

**Business Scenario:**
A user exports a CodeSystem with multiple values for properties (e.g., multiple synonyms, multiple type codings). The export uses order suffixes. When importing, the system must maintain the correct order and pair coding values correctly.

**Expected Behavior:**
- Multiple property values are imported in correct order
- Multiple designations are imported with correct order
- Coding properties are paired correctly (code##1 with system##1, code##2 with system##2)
- Incomplete pairs (e.g., code##2 present but system##2 empty) are handled gracefully

---

### Test 3: TSV Import with New Format Columns

**Business Purpose:**
Validates that TSV (Tab-Separated Values) files can be imported with the new column format.

**Key Validations:**
- TSV file parsing works correctly (tab-separated instead of comma-separated)
- All column formats work correctly with TSV
- Data integrity is maintained across file formats

**Business Scenario:**
A user has a TSV file exported from another system or created manually. The system must be able to import this file with the same column format support as CSV.

**Expected Behavior:**
- TSV files are parsed correctly
- All column formats (designations, properties, coding) work identically to CSV
- Data is imported correctly

---

### Test 4: XLSX Import with New Format Columns

**Business Purpose:**
Validates that XLSX (Excel) files can be imported with the new column format.

**Key Validations:**
- XLSX file parsing works correctly
- Excel format is properly handled
- All column formats work correctly with XLSX

**Business Scenario:**
A user exports a CodeSystem to Excel format or receives an Excel file from another system. The system must be able to import this file with full support for the new column format.

**Expected Behavior:**
- XLSX files are parsed correctly
- All column formats work identically to CSV/TSV
- Data is imported correctly

**Note:** The current test uses CSV as a proxy for XLSX structure testing. A full XLSX test would require creating actual Excel files using Apache POI.

---

### Test 5: Coding Property Pairing Logic

**Business Purpose:**
Validates that code and system columns are correctly paired by order, ensuring coding properties are created correctly.

**Key Validations:**
- Code and system columns are paired by order (##1 with ##1, ##2 with ##2)
- Multiple coding values are correctly ordered
- Missing code or system values are handled correctly (incomplete pairs are skipped)

**Business Scenario:**
A user imports a file with coding properties. The system must correctly pair code and system columns that have the same property name and order, creating proper coding values. If a pair is incomplete (e.g., code##2 exists but system##2 is empty), the system should handle this gracefully.

**Expected Behavior:**
- Coding properties are correctly paired by order
- Complete pairs create coding values with both code and system
- Incomplete pairs are handled (only complete pairs create values)

---

### Test 6: Designation Parsing with Multiple Languages and Orders

**Business Purpose:**
Validates that designations are correctly parsed by type, language, and order, supporting multilingual content.

**Key Validations:**
- Designations are correctly parsed by type, language, and order
- Multiple designations of the same type/language are correctly ordered
- Designations in different languages are correctly separated
- Order suffixes work correctly for designations

**Business Scenario:**
A user imports a CodeSystem with multilingual designations. For example, a concept may have multiple English definitions and one Russian definition. The system must correctly import all designations with their correct type, language, and order.

**Expected Behavior:**
- All designations are imported with correct type and language
- Multiple designations of the same type/language maintain their order
- Designations in different languages are stored separately

---

### Test 7: Property Value Type Transformation

**Business Purpose:**
Validates that property values are correctly transformed to their appropriate types (string, integer, decimal, boolean, dateTime).

**Key Validations:**
- String values are kept as-is
- Integer values are correctly parsed
- Decimal values are correctly parsed
- Boolean values are correctly parsed (1/true = true, others = false)
- DateTime values are correctly parsed with format string

**Business Scenario:**
A user imports a file with various property types. The system must correctly transform the string values from the file into their appropriate types based on the property configuration.

**Expected Behavior:**
- All property types are correctly transformed
- Type-specific parsing works correctly (integers, decimals, booleans, dates)
- Invalid values are handled appropriately (may cause validation errors)

---

### Test 8: Error Handling for Missing Columns

**Business Purpose:**
Validates that the system properly detects and reports errors when configured columns are missing from the import file.

**Key Validations:**
- Missing configured columns throw appropriate errors
- Error messages include the missing column name
- Error code is correct (TE712)

**Business Scenario:**
A user configures an import with a property mapping to a column, but the file doesn't contain that column. The system must detect this and provide a clear error message.

**Expected Behavior:**
- System throws an exception with error code TE712
- Error message includes the missing column name
- Import process stops with clear error indication

---

### Test 9: Auto Concept Order Feature

**Business Purpose:**
Validates that when auto concept order is enabled, concepts are assigned order values based on their row position in the file.

**Key Validations:**
- Concepts get order values based on row position
- Order formula: (rowIndex + 1) * 10
- Order is added as a property value

**Business Scenario:**
A user enables auto concept order during import. The system should automatically assign order values to concepts based on their position in the file, making it easy to maintain the original order.

**Expected Behavior:**
- Each concept gets a `conceptOrder` property
- Order values follow the formula: (rowIndex + 1) * 10
- First row gets order 10, second gets 20, etc.

---

### Test 10: Coding Columns Priority Over Designation Columns

**Business Purpose:**
Validates that columns with `#code` or `#system` are correctly identified as property columns first, preventing false matches with designation columns.

**Key Validations:**
- Columns with `#code` or `#system` are identified as property columns first
- This prevents false matches with designation columns
- Both property and designation columns can coexist with similar names

**Business Scenario:**
A user has both a coding property named "type" and a designation with type "type" and language "code". The column `type#code##1` should be treated as a coding property column, while `type#code` (without order suffix) could be a designation. The system must correctly distinguish between these.

**Expected Behavior:**
- Coding property columns (with `#code` or `#system`) are processed as properties
- Designation columns (with `type#language` pattern) are processed as designations
- Both can coexist without conflicts

---

## Test Suite: CodeSystemFileImportServiceDryRunTest

These tests target **`CodeSystemFileImportService.save()`** when **`dryRun` (validate data only)** is enabled: the UI step that validates the file and can show a **diff** without persisting a full import on the main transaction.

Implementation note: the real shadow import + version compare runs in **`CodeSystemFileImportDryRunService`** inside a **`REQUIRES_NEW`** transaction so rollback of the temporary data does not mark the outer Spring transaction rollback-only (avoiding `UnexpectedRollbackException` on commit). The tests **mock** that service and assert wiring and outcomes at the service layer.

### Test 1: Dry run sets diff and does not call persistent import

**Business Purpose:**  
Ensures that with “validate data only”, the response **`diff`** text comes from the dry-run path and **`CodeSystemImportService.importCodeSystem`** is **not** invoked for the real (non-shadow) payload—so nothing is persisted as a final import in this mode.

**Key Validations:**
- `save()` calls `CodeSystemFileImportDryRunService.dryRunImportCompareAndRollback` with the expected code system id.
- Returned `CodeSystemFileImportResponse` contains the mocked diff string.
- Error list is empty when the dry-run result has no import error.

**Business Scenario:**  
A user runs **Validate data only** after mapping columns; the backend should return a comparison/diff string for review and must not perform the same persistent import as **Import** / non–dry-run.

**Expected Behavior:**
- `diff` is populated from the dry-run result.
- No call to persistent `importCodeSystem` on `CodeSystemImportService` for the mapped CodeSystem in this request.

---

### Test 2: Dry run adds TE716 when shadow import path fails

**Business Purpose:**  
When the shadow import/compare path fails (e.g. exception during temporary import), the service should surface **`TE716`** (exception during CodeSystem import) on the response **errors** list, with **no** diff.

**Key Validations:**
- `DryRunResult` with `importError` present adds that issue to `response.errors`.
- `diff` is null.
- Persistent `importCodeSystem` is still not called.

**Business Scenario:**  
Validation fails inside the isolated dry-run transaction; the user sees a structured error (TE716) instead of a successful diff.

**Expected Behavior:**
- Exactly one error with code **TE716**.
- No diff text.

---

## Test Coverage Summary

### File Formats Covered
- ✅ CSV (Comma-Separated Values)
- ✅ TSV (Tab-Separated Values)
- ✅ XLSX (Excel format - structure tested)

### Column Formats Covered
- ✅ Designations: `type#language` and `type#language##order`
- ✅ Simple properties: `property` and `property##order`
- ✅ Coding properties: `property#code`/`property#system` and `property#code##order`/`property#system##order`

### Property Types Covered
- ✅ String
- ✅ Integer
- ✅ Decimal
- ✅ Boolean
- ✅ DateTime
- ✅ Coding

### Edge Cases Covered
- ✅ Empty cells
- ✅ Missing columns
- ✅ Incomplete coding pairs
- ✅ Multiple values with order
- ✅ Single values without order
- ✅ Auto concept order
- ✅ Column priority (coding vs designation)

### Error Handling Covered
- ✅ Missing column errors
- ✅ Configuration validation
- ✅ Dry-run (validate only): diff vs TE716 when shadow import fails (service-level)

---

## Review Checklist

Please review the following aspects:

1. **Business Logic**: Do the tests cover all important business scenarios?
2. **Edge Cases**: Are there additional edge cases that should be tested?
3. **Error Scenarios**: Are there additional error scenarios that should be covered?
4. **File Formats**: Should we add tests for additional file formats?
5. **Performance**: Should we add performance tests for large files?
6. **Integration**: Should we add integration tests that test the full import flow?
7. **Dry run**: Do dry-run tests (mocked `CodeSystemFileImportDryRunService`) match your expectations for “validate data only” vs full import?

---

---

## Test Suite: CodeSystemFileImportMapperTest

The mapper tests validate the transformation from parsed import file data (intermediate `CodeSystemFileImportResult`) to actual CodeSystem domain objects (`Concept`, `Designation`, `EntityPropertyValue`, `CodeSystemAssociation`). These tests ensure end-to-end mapping functionality.

### Test 1: Basic Concept Mapping with Display Designation

**Business Purpose:**
Validates that basic concepts with display designations are correctly mapped from parsed file data to domain objects, following Estonian migration patterns.

**Inspired by:** `vastsyndinu-ajalisus` from `pub_resources.csv` (Kood=concept-code, Nimetus=display|et)

**Key Validations:**
- Concept.code is correctly set from concept-code property
- Display designation is created with correct type, language, preferred flag
- Designation status is set to active
- Case significance is set correctly

**Business Scenario:**
A user imports a CodeSystem with basic concepts that have display names. The system must correctly map the parsed data to Concept objects with proper Designation objects attached.

**Expected Behavior:**
- Concepts are created with correct codes
- Display designations are created with preferred=true, status=active, caseSignificance=ci
- All designation attributes are correctly set

---

### Test 2: Concept Mapping with Parent Hierarchy Association

**Business Purpose:**
Validates that parent-child relationships are correctly mapped to CodeSystemAssociation objects.

**Inspired by:** `immuniseerimise-korvalnahud` (Vanem_kood=parent)

**Key Validations:**
- Parent association is created with type 'is-a'
- Association targetCode is set to parent code
- Association status is set to active

**Business Scenario:**
A user imports a hierarchical CodeSystem where concepts have parent relationships. The system must create CodeSystemAssociation objects to represent these relationships.

**Expected Behavior:**
- Associations are created with correct type and target code
- Associations have active status
- Parent-child relationships are correctly represented

---

### Test 3: Concept Mapping with Definition Designation

**Business Purpose:**
Validates that concepts with both display and definition designations are correctly mapped.

**Inspired by:** `poordumise-erakorralisus` (Pikk_nimetus=definition|et, Nimetus=display|et)

**Key Validations:**
- Multiple designations are created (display and definition)
- Display designation has preferred=true
- Definition designation has preferred=false
- Both designations have correct type and language

**Business Scenario:**
A user imports a CodeSystem where concepts have both display names and definitions. The system must create separate Designation objects for each, with the display marked as preferred.

**Expected Behavior:**
- Both designations are created with correct attributes
- Display is marked as preferred
- Definition is not preferred
- Both have correct type and language

---

### Test 4: Concept Mapping with Custom String Property

**Business Purpose:**
Validates that custom string properties are correctly mapped to EntityPropertyValue objects.

**Inspired by:** `syndimiskoht` (Selgitus=comment)

**Key Validations:**
- Custom string property is mapped to EntityPropertyValue
- Property value is correctly stored
- Property is not treated as designation or association

**Business Scenario:**
A user imports a CodeSystem with custom properties (e.g., comments, notes). The system must create EntityPropertyValue objects for these properties, separate from designations and associations.

**Expected Behavior:**
- Custom properties are stored as EntityPropertyValue objects
- Values are correctly preserved
- Properties are not confused with designations or associations

---

### Test 5: Coding Property Mapping (New Format)

**Business Purpose:**
Validates that coding properties with code/system pairs are correctly mapped to EntityPropertyValue objects with coding type.

**Key Validations:**
- Coding properties with code/system pairs are correctly mapped
- Multiple coding values are correctly ordered
- Coding value contains both code and codeSystem

**Business Scenario:**
A user imports a CodeSystem with coding properties (references to other code systems). The system must create EntityPropertyValue objects with coding type, containing both code and codeSystem values.

**Expected Behavior:**
- Coding property values are created with correct structure
- Multiple coding values maintain their order
- Both code and codeSystem are correctly stored

---

### Test 6: Multiple Designations Mapping (New Format)

**Business Purpose:**
Validates that multiple designations of the same or different types are correctly mapped.

**Key Validations:**
- Multiple designations of same type/language are correctly ordered
- Designations in different languages are correctly separated
- Display designation is preferred

**Business Scenario:**
A user imports a CodeSystem with multiple designations per concept (e.g., multiple definitions in the same language, or definitions in different languages). The system must create separate Designation objects for each, maintaining order and language separation.

**Expected Behavior:**
- All designations are created with correct attributes
- Order is maintained for multiple designations of same type/language
- Languages are correctly separated
- Display is marked as preferred

---

### Test 7: Status Mapping with Various Formats

**Business Purpose:**
Validates that status values in various formats are correctly mapped via PublicationStatus.getStatus().

**Key Validations:**
- Status values are correctly mapped via PublicationStatus.getStatus()
- Various status formats (1, 0, A, active, retired) are handled

**Business Scenario:**
A user imports a CodeSystem where status values are provided in different formats (numeric, alphabetic, text). The system must normalize these to standard PublicationStatus values (active, draft, retired).

**Expected Behavior:**
- Status values are correctly normalized
- All supported formats are handled
- Concept versions have correct status

---

### Test 8: Hierarchical Concept with Inferred Parent

**Business Purpose:**
Validates that hierarchical concepts (using hierarchical-concept property) correctly infer parent relationships from prefix matching.

**Key Validations:**
- Hierarchical concepts infer parent from prefix matching
- Parent association is created with type 'is-a'

**Business Scenario:**
A user imports a CodeSystem using hierarchical-concept property where parent relationships are inferred from code prefixes (e.g., "1" is parent of "11", "11" is parent of "111"). The system must create associations based on this inference.

**Expected Behavior:**
- Parent relationships are correctly inferred
- Associations are created with correct target codes
- Hierarchical structure is correctly represented

---

### Test 9: CodeSystem and Version Metadata Mapping

**Business Purpose:**
Validates that CodeSystem and CodeSystemVersion metadata is correctly mapped from import request to domain objects.

**Key Validations:**
- CodeSystem metadata (id, uri, publisher, name, title, OID) is correctly mapped
- CodeSystemVersion metadata (version number, release date, algorithm, supported languages) is correctly mapped

**Business Scenario:**
A user imports a CodeSystem with full metadata (OID, publisher, version information). The system must correctly map all metadata to the CodeSystem and CodeSystemVersion objects.

**Expected Behavior:**
- All CodeSystem metadata is correctly set
- All CodeSystemVersion metadata is correctly set
- Identifiers (OIDs) are correctly formatted
- Supported languages are correctly extracted from designations

---

### Test 10: Full End-to-End Scenario with All Property Types

**Business Purpose:**
Validates a comprehensive scenario with all property types, designations, and associations correctly mapped.

**Key Validations:**
- All property types (string, decimal, bool, coding) are correctly mapped
- Designations, properties, and associations are all correctly mapped

**Business Scenario:**
A user imports a CodeSystem with a complete set of data including designations, custom properties, associations, and various property types. The system must correctly map all elements to their respective domain objects.

**Expected Behavior:**
- All designations are correctly mapped
- All property values are correctly mapped with correct types
- All associations are correctly mapped
- Complete concept structure is correctly represented

---

### Test 11: Date Format Detection and Parsing (PDF Requirement)

**Business Purpose:**
Validates that date values in different formats are correctly parsed according to configured format.

**Key Validations:**
- Date values in different formats are correctly parsed
- Format is correctly applied from property configuration

**Business Scenario:**
A user imports a CodeSystem with date properties in various formats (YYYY-MM-DD, DD.MM.YY, DD.MM.YYYY, MM/DD/YYYY). The system must correctly parse each date according to its configured format.

**Expected Behavior:**
- All date formats are correctly parsed
- Dates are converted to Date objects
- Format configuration is correctly applied

---

### Test 12: Delimiter Handling for String Properties (PDF Requirement)

**Business Purpose:**
Validates that delimiter-separated string values are correctly split into multiple property values.

**Key Validations:**
- Delimiter-separated values are split correctly
- Each part is trimmed
- Multiple property values are created

**Business Scenario:**
A user imports a CodeSystem where a string property contains multiple values separated by a delimiter (e.g., "value1|value2|value3"). The system must split these into separate EntityPropertyValue objects, one for each part.

**Expected Behavior:**
- String is split at delimiter
- Each part is trimmed
- Multiple EntityPropertyValue objects are created
- All values are correctly stored

---

### Test 13: Boolean Detection and Parsing (PDF Requirement)

**Business Purpose:**
Validates that boolean values in various formats are correctly parsed.

**Key Validations:**
- Boolean values are correctly parsed (1/true = true, 0/false = false)
- Various boolean formats are handled

**Business Scenario:**
A user imports a CodeSystem with boolean properties in various formats (1/0, true/false). The system must correctly parse these to boolean values.

**Expected Behavior:**
- All boolean formats are correctly parsed
- Values are converted to true/false
- Boolean type is correctly applied

---

### Test 14: Identifier Column Auto-Detection (PDF Requirement)

**Business Purpose:**
Validates that columns named "id", "code", "identifier", or "kood" can be correctly mapped to concept-code property.

**Key Validations:**
- Columns with identifier-like names are correctly mapped to concept-code
- This is handled at the configuration level, but end result is validated

**Business Scenario:**
A user imports a CodeSystem where the identifier column has different names (id, code, identifier, kood). The system must correctly map these to the concept-code property, allowing flexible column naming.

**Expected Behavior:**
- Identifier columns are correctly recognized
- Concepts are created with correct codes
- Mapping works regardless of column name variant

---

### Test 15: Multiple Date Formats in Same Import

**Business Purpose:**
Validates that multiple date columns with different formats are correctly parsed simultaneously.

**Key Validations:**
- Multiple date columns with different formats are correctly parsed
- Each date column uses its configured format

**Business Scenario:**
A user imports a CodeSystem with multiple date properties, each using a different format (e.g., created date in YYYY-MM-DD, modified date in DD.MM.YYYY). The system must correctly parse each date according to its specific format configuration.

**Expected Behavior:**
- All date columns are correctly parsed
- Each uses its configured format
- No format conflicts occur
- All dates are correctly converted to Date objects

---

## Test Coverage Summary (Updated)

### File Formats Covered
- ✅ CSV (Comma-Separated Values)
- ✅ TSV (Tab-Separated Values)
- ✅ XLSX (Excel format - structure tested)

### Column Formats Covered
- ✅ Designations: `type#language` and `type#language##order`
- ✅ Simple properties: `property` and `property##order`
- ✅ Coding properties: `property#code`/`property#system` and `property#code##order`/`property#system##order`

### Property Types Covered
- ✅ String
- ✅ Integer
- ✅ Decimal
- ✅ Boolean
- ✅ DateTime (with multiple format support)
- ✅ Coding

### Mapping Functionality Covered
- ✅ Concept mapping with code and description
- ✅ Designation mapping (display, definition, multiple languages)
- ✅ Property value mapping (all types)
- ✅ Association mapping (parent, hierarchical)
- ✅ Status mapping (various formats)
- ✅ CodeSystem metadata mapping
- ✅ CodeSystemVersion metadata mapping

### PDF Requirements Covered
- ✅ Date format support (YYYY-MM-DD, DD.MM.YY, DD.MM.YYYY, MM/DD/YYYY)
- ✅ Delimiter handling (max 3 chars, split/trim/validate)
- ✅ Boolean detection (1/0, true/false)
- ✅ Identifier auto-detection (id, code, identifier, kood)

### Edge Cases Covered
- ✅ Empty cells
- ✅ Missing columns
- ✅ Incomplete coding pairs
- ✅ Multiple values with order
- ✅ Single values without order
- ✅ Auto concept order
- ✅ Column priority (coding vs designation)
- ✅ Multiple date formats
- ✅ Delimiter-separated values
- ✅ Various status formats

### Error Handling Covered
- ✅ Missing column errors
- ✅ Configuration validation
- ✅ Dry-run (validate only): diff vs TE716 when shadow import fails (service-level)

---

## Review Checklist

Please review the following aspects:

1. **Business Logic**: Do the tests cover all important business scenarios?
2. **Edge Cases**: Are there additional edge cases that should be tested?
3. **Error Scenarios**: Are there additional error scenarios that should be covered?
4. **File Formats**: Should we add tests for additional file formats?
5. **Performance**: Should we add performance tests for large files?
6. **Integration**: Should we add integration tests that test the full import flow?
7. **PDF Requirements**: Are all PDF requirements (pages 190-192) adequately covered?
8. **Dry run**: Do dry-run tests (mocked `CodeSystemFileImportDryRunService`) match your expectations for “validate data only” vs full import?

---

## Next Steps

After review:
1. Add any missing test scenarios
2. Enhance existing tests if needed
3. Add integration tests if required
4. Add performance tests for large file imports
5. Update documentation based on test findings
