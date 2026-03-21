# CodeSystem Concept Export Specification

## Version
**1.0.2** (Breaking Change - New Format with Validation and Column Ordering)

## Overview

The CodeSystem Concept Export functionality exports concepts from a CodeSystem version to CSV or XLSX format. The export includes:
- Concept codes
- Designations (display, definition, etc.)
- Entity property values (simple types and coding types)
- Concept associations (parent/child relationships)
- Status information

## Export Formats

- **CSV**: Comma-separated values with UTF-8 encoding
- **XLSX**: Excel format with worksheet named "concepts"

## Column Naming Conventions

### Base Column
- `code` - The concept code (always first column)

### Designation Columns
Format: `{designationType}#{language}##{order}` (when multiple) or `{designationType}#{language}` (when single)

- `{designationType}` - The designation type (e.g., "display", "definition")
- `{language}` - The language code (e.g., "en", "ru", "ru-RU"). Empty string if language is null
- `{order}` - **Optional** sequential number starting from 1 for multiple designations of the same type and language. Skipped when there's only one designation of that type#language combination (when maxCount == 1).

**Examples:**
- `display#en` - Single English display designation (order suffix skipped)
- `display#en##1`, `display#en##2` - Multiple English display designations (order suffix required)
- `definition#en` - Single English definition designation (order suffix skipped)
- `definition#en##1`, `definition#en##2` - Multiple English definition designations (order suffix required)
- `definition#ru` - Single Russian definition designation (order suffix skipped)

### Property Columns

#### Simple Property Types
Format: `{propertyName}##{order}` (when multiple) or `{propertyName}` (when single)

- `{propertyName}` - The property name
- `{order}` - **Optional** sequential number starting from 1 for multiple values of the same property. Skipped when there's only one value for that property (when maxCount == 1).

**Examples:**
- `itemWeight` - Single itemWeight value (order suffix skipped)
- `synonym` - Single synonym value (order suffix skipped)
- `synonym##1`, `synonym##2` - Multiple synonym values (order suffix required)

#### Coding Property Types
Format: `{propertyName}#code##{order}` and `{propertyName}#system##{order}` (when multiple) or `{propertyName}#code` and `{propertyName}#system` (when single)

- `{propertyName}` - The property name
- `code` or `system` - Indicates whether this column contains the code or system
- `{order}` - **Optional** sequential number starting from 1 for multiple coding values. Skipped when there's only one coding value for that property (when maxCount == 1).

**Examples:**
- `type#code`, `type#system` - Single type coding value (order suffix skipped)
- `type#code##1`, `type#system##1`, `type#code##2`, `type#system##2` - Multiple type coding values (order suffix required)

### Special Columns
- `status` - Concept status (active, draft, retired, etc.)
- `is-a` - Parent concepts (comma-separated)
- `parent` - Parent concepts (comma-separated)
- `child` - Child concepts (comma-separated)
- `partOf` - Part-of relationships (comma-separated)
- `groupedBy` - Grouped-by relationships (comma-separated)
- `classifiedWith` - Classified-with relationships (comma-separated)

## Column Ordering

Columns appear in the following order:

1. **Base column**: `code` (always first)
2. **Display designation columns**: All `display#{language}` columns, sorted alphabetically by `{language}` (with or without order suffix)
3. **Other designation columns**: All non-display designation columns (e.g., `definition`, `alias`), sorted alphabetically by `{type}#{language}` (with or without order suffix)
4. **Property columns**: 
   - Ordered by their position in the CodeSystem properties definition (`codeSystem.getProperties()`)
   - Properties not in the definition are sorted alphabetically after defined properties
   - For coding properties: `code` column comes before `system` column for the same order
   - Within the same property: order 1 comes before order 2, etc.
   - Example: `itemWeight`, `synonym`, `synonym##2`, `type#code`, `type#system`, `type#code##2`, `type#system##2` (when single values skip suffix)
   - Example: `itemWeight##1`, `synonym##1`, `synonym##2`, `type#code##1`, `type#system##1`, `type#code##2`, `type#system##2` (when multiple values use suffix)
5. **Special columns**: `status`, `is-a`, `parent`, `child`, `partOf`, `groupedBy`, `classifiedWith`

## Data Formatting

### Designations
- **Value**: The designation name/value (`Designation.getName()`)
- **Empty cells**: Empty string if no designation exists at that order position
- **Filtering**: Both `active` and `draft` designations with non-null, non-empty `designationType` are included

### Simple Property Types

#### String
- **Value**: The string value as-is
- **Empty cells**: Empty string if property value is null or missing

#### Decimal
- **Value**: Plain decimal format (no scientific notation)
- **Format**: Uses `BigDecimal.toPlainString()` to avoid scientific notation (e.g., `10` instead of `1E+1`)
- **Trailing zeros**: Removed (e.g., `10.00` becomes `10`)
- **Examples**: `10`, `3.14`, `0.5`

#### DateTime
- **Value**: Local date portion only (YYYY-MM-DD format)
- **Format**: `OffsetDateTime.toLocalDate().toString()`
- **Example**: `2026-03-20`

#### Other Types
- **Value**: JSON representation if not a string
- **Format**: `JsonUtil.toJson(value)`

### Coding Property Types
- **Code column**: The coding code value (`EntityPropertyValueCodingValue.getCode()`)
- **System column**: The coding system URI (`EntityPropertyValueCodingValue.getCodeSystem()`)
- **Empty cells**: Empty string if coding value is null or cannot be parsed
- **Error handling**: If parsing fails, empty string is returned

### Special Columns

#### Status
- **Value**: Status from the first concept version
- **Format**: Status enum value (e.g., "active", "draft", "retired")
- **Empty**: Empty string if no version exists

#### Association Columns (is-a, parent, child, partOf, groupedBy, classifiedWith)
- **Value**: Comma-separated list of target concept codes
- **Format**: `String.join("#", codes)`
- **Filtering**: Only active associations are included
- **Empty**: Empty string if no associations exist

## Header Generation Logic

### Designation Headers
1. Scan up to 1000 concepts to find all designation type#language combinations
2. Count maximum occurrences per `{type}#{language}` combination
3. Generate columns: `{type}#{language}##1`, `{type}#{language}##2`, ..., `{type}#{language}##{maxCount}` (or `{type}#{language}` when maxCount == 1)
4. Separate display designations from other designations
5. Sort display designation columns alphabetically
6. Sort other designation columns alphabetically
7. Add display columns first, then other designation columns

### Property Headers
1. Scan up to 1000 concepts to find all property names
2. Determine property type from first occurrence
3. Count maximum occurrences per property name
4. Generate columns based on property type:
   - **Simple types**: `{propertyName}##1`, `{propertyName}##2`, ... (or `{propertyName}` when maxCount == 1)
   - **Coding types**: `{propertyName}#code##1`, `{propertyName}#system##1`, `{propertyName}#code##2`, `{propertyName}#system##2`, ... (or `{propertyName}#code` and `{propertyName}#system` when maxCount == 1)
5. Sort property columns:
   - By their order in CodeSystem properties definition (`codeSystem.getProperties()`)
   - Properties not in definition sorted alphabetically after defined properties
   - Within same property: by order (1 before 2)
   - For coding properties: code before system for same order

## Row Generation Logic

### Designation Values
1. Group designations by `{type}#{language}` key
2. For each designation column `{type}#{language}##{order}`:
   - Find the designation group matching `{type}#{language}`
   - Get the designation at index `{order} - 1` (0-based)
   - Extract the name/value
   - If no designation at that order, use empty string

### Property Values
1. Group property values by property name
2. For each property column:
   - **Column Type Detection**: Headers containing `#code` or `#system` are identified as property columns (coding properties) and processed first, before designation columns. This prevents false matches (e.g., `type#code##1` would match designation pattern but is actually a coding property).
   - Parse column header to extract property name, type, and order
   - Find the property value group matching the property name
   - Get the property value at index `{order} - 1` (0-based)
   - Format the value according to property type:
     - **Coding**: Extract code or system based on column suffix (`#code` or `#system`)
     - **DateTime**: Extract local date
     - **Decimal**: Format as plain string
     - **String**: Use as-is
     - **Other**: Convert to JSON
   - If no value at that order, use empty string

## Filtering Rules

### Designations
- Designations with `status == PublicationStatus.active` OR `status == "draft"` are included
- Only designations with non-null, non-empty `designationType` (after trimming) are included
- Language can be null or empty (treated as empty string in column name)

### Property Values
- Only property values with non-null, non-empty `entityProperty` are included
- Property type is determined from the first occurrence of each property name

### Associations
- Only associations with `status == PublicationStatus.active` are included

## Examples

### Example 1: Simple Concept

**Concept:**
- Code: `a1`
- Display: "A1" (en)
- Property: `itemWeight = 10` (decimal)
- Properties: `synonym = "x"`, `synonym = "Y"` (string)

**Export:**
```csv
code,display#en,itemWeight,synonym,synonym##2
a1,A1,10,x,Y
```

### Example 2: Concept with Coding Properties

**Concept:**
- Code: `a1`
- Display: "A1" (en)
- Properties: 
  - `type = Coding(code="AdverseEvent", system="http://hl7.org/fhir/fhir-types")`
  - `type = Coding(code="Age", system="http://hl7.org/fhir/fhir-types")`

**Export:**
```csv
code,display#en,type#code##1,type#system##1,type#code##2,type#system##2
a1,A1,AdverseEvent,http://hl7.org/fhir/fhir-types,Age,http://hl7.org/fhir/fhir-types
```

**Note**: Since there are multiple coding values (maxCount == 2), the order suffix is required for all columns (`##1`, `##2`).

### Example 3: Concept with Multiple Designations

**Concept:**
- Code: `b1`
- Display: "B1" (en)
- Definitions: "bar-bar" (en), "bar" (en), "бар" (ru)
- Properties: `synonym = "g1"`, `synonym = "g2"`

**Export:**
```csv
code,display#en,definition#en,definition#en##2,definition#ru,synonym,synonym##2
b1,B1,bar-bar,bar,бар,g1,g2
```

### Example 4: Complete Example

**Concepts:**
- `a1`: Display "A1" (en), itemWeight=10, synonyms=["x", "Y"], types=[AdverseEvent, Age]
- `b1`: Display "B1" (en), definitions=["bar-bar", "bar"] (en), definition="бар" (ru), synonyms=["g1", "g2"], types=[Account, ActivityDefinition]

**Export:**
```csv
code,display#en,definition#en,definition#en##2,definition#ru,itemWeight,synonym##1,synonym##2,type#code##1,type#system##1,type#code##2,type#system##2
a1,A1,,,,10,x,Y,AdverseEvent,http://hl7.org/fhir/fhir-types,Age,http://hl7.org/fhir/fhir-types
b1,B1,bar-bar,bar,бар,,g1,g2,Account,http://hl7.org/fhir/fhir-types,ActivityDefinition,http://hl7.org/fhir/fhir-types
```

**Note**: 
- Since there are multiple `synonym` values (maxCount == 2), the order suffix is required (`synonym##1`, `synonym##2`)
- Since there are multiple `type` coding values (maxCount == 2), the order suffix is required for all columns (`type#code##1`, `type#system##1`, `type#code##2`, `type#system##2`)

## Import Compatibility

The export format is designed to be compatible with the import functionality. The import processor recognizes:

1. **Designation columns**: `{type}#{language}##{order}` format (with suffix) or `{type}#{language}` format (without suffix for single values)
2. **Simple property columns**: `{propertyName}##{order}` format (with suffix) or `{propertyName}` format (without suffix for single values)
3. **Coding property columns**: `{propertyName}#code##{order}` and `{propertyName}#system##{order}` format (with suffix) or `{propertyName}#code` and `{propertyName}#system` format (without suffix for single values)

The import processor:
- Parses column headers to extract type, language, property name, and order
- Supports both formats: with `##{order}` suffix and without (defaults to order 1 when suffix is skipped)
- Reconstructs designations and property values in the correct order
- Combines code and system columns for coding properties

## Validation Requirements

### Designation Validation
- **Requirement**: The export service validates that all designations present in the database are included in the export output structure (headers)
- **Validation Point**: After generating headers and before generating the final output (in `composeResult()` method)
- **Action**: If at least one designation is missing from the export structure, an exception is thrown (ApiError.TE808)
- **Exception Details**: The exception includes comprehensive diagnostic information:
  - **Code system ID**: The identifier of the CodeSystem being exported
  - **Code system description**: The title (English) or name, falling back to ID if neither is available
  - **Missing designations**: Comma-separated list of missing designations in format `conceptCode:type#language`
  - **Diagnostic information**: Structured log message containing:
    - CodeSystem ID
    - CodeSystem description
    - Total number of concepts in the export
    - Number of concepts that have designations
    - List of all missing designations
- **Validation Logic**:
  1. Collect all active and draft designations from all concepts in the export
  2. For each designation, check if a corresponding column exists in headers
  3. A designation matches a header if:
     - The header exactly matches `{type}#{language}` (for single values without suffix)
     - The header starts with `{type}#{language}##` (for multiple values with order suffix)
  4. If any designation has no matching header, collect it as missing
  5. If any missing designations are found, throw exception with diagnostic information
  6. Additionally, if there are any designations (active or draft) in the database but no designation columns in the export headers, throw exception
- **Example**: If concept "a1" has a designation `display#en` in the database but the export headers don't include a `display#en` or `display#en##1` column, an exception is thrown with:
  - CodeSystem ID: "test1"
  - Description: "Test1" (from title or name)
  - Missing designations: "a1:display#en"
  - Diagnostic info: "CodeSystem Structure - ID: test1, Description: Test1, Total concepts: 2, Concepts with designations: 2, Missing designations: a1:display#en"

## Error Handling

### Designation Validation Errors
- **Error Code**: TE808
- **Error Message**: "Export failed: at least one designation is missing from export output. CodeSystem: {{codeSystemId}}, Description: {{description}}, Missing designations: {{missingDesignations}}"
- **When**: If any designation from the database is missing in the export output structure (headers)
- **Exception Details**: 
  - Includes code system ID, description, and list of missing designations
  - Includes comprehensive diagnostic information about CodeSystem structure:
    - Total number of concepts
    - Number of concepts with designations
    - Complete list of missing designations with concept codes
- **Diagnostic Value**: The diagnostic information helps identify:
  - Whether the issue is data-related (designations not loaded from database)
  - Whether the issue is header generation-related (designations loaded but headers not generated correctly)
  - Which specific concepts and designation types are affected

### Designation Parsing Errors
- If designation column cannot be parsed, empty string is returned
- If designation order is out of bounds, empty string is returned

### Property Value Parsing Errors
- If property column cannot be parsed, empty string is returned
- If property value order is out of bounds, empty string is returned
- If coding value cannot be parsed, empty string is returned
- If property value type conversion fails, empty string is returned

### Data Loading Errors
- If concept has no versions, status column is empty
- If designation/property list is null, treated as empty list
- Null values are handled gracefully (empty strings in output)

## Performance Considerations

- Header generation scans up to **1000 concepts** to determine maximum column counts
- This ensures reasonable performance for large CodeSystems while still capturing most column variations
- For CodeSystems with more than 1000 concepts, columns are generated based on the first 1000 concepts

## Breaking Changes

This specification represents a **breaking change** from previous export formats:

1. **Column naming**: Changed from concatenated format to order-based format
2. **Coding properties**: Split into separate code and system columns
3. **Designations**: Now included with order-based format
4. **Column ordering**: Changed to group code/system together for coding properties
5. **Optional order suffix**: Order suffix (`##{order}`) is skipped when there's only one value, making the format cleaner for common single-value cases

## Migration Notes

- Old export files will not be compatible with the new import format
- Users must re-export CodeSystems to get the new format
- The import processor supports both old and new formats for backward compatibility during transition
- The import processor supports both formats: with `##{order}` suffix (for backward compatibility) and without suffix (new cleaner format for single values)

## Implementation Details

### Key Classes
- `ConceptExportService`: Main export service
- `ConceptService`: Provides concept data
- `CodeSystemService`: Provides CodeSystem metadata

### Key Methods
- `composeHeaders()`: Generates column headers based on concept data
- `composeRow()`: Generates row data for a concept
  - **Column Processing Order**: Property columns (with `#code` or `#system`) are processed before designation columns to prevent false matches
  - Headers containing `#code` or `#system` are identified as property columns first
  - Only headers without `#code` or `#system` are checked as designation columns
- `parseColumnHeader()`: Parses property column headers (supports both with and without `##{order}` suffix)
- `parseDesignationColumn()`: Parses designation column headers (supports both with and without `##{order}` suffix)
- `validateDesignationsPresent()`: Validates that all designations from database are present in export output structure
  - Collects all active and draft designations from all concepts
  - Checks each designation against generated headers
  - Throws ApiError.TE808 with diagnostic information if any designation is missing
  - Diagnostic information includes CodeSystem ID, description, concept counts, and missing designation list

### Data Models
- `Concept`: Represents a concept with versions
- `CodeSystemEntityVersion`: Represents a concept version with designations and properties
- `Designation`: Represents a designation with type, language, and value
- `EntityPropertyValue`: Represents a property value with type-specific data
