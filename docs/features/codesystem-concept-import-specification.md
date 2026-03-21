# CodeSystem Concept Import Specification

## Version
**1.0.3** (Compatible with Export Format 1.0.2)

## Overview

The CodeSystem Concept Import functionality imports concepts from CSV, TSV, or XLSX files into a CodeSystem version. The import processor supports both the new export format (with optional order suffixes) and legacy formats for backward compatibility.

## Supported File Formats

- **CSV**: Comma-separated values with UTF-8 encoding
  - Column separator: semicolon (`;`) or comma (`,`)
- **TSV**: Tab-separated values with UTF-8 encoding
  - Column separator: Tab character
- **XLSX**: Excel format with worksheet named "concepts" (or first worksheet)

## Column Naming Conventions

The import processor recognizes columns in the following formats:

### Base Column
- `code` - The concept code (required, used as identifier)

### Designation Columns

#### New Format (Recommended)
Format: `{designationType}#{language}##{order}` (when multiple) or `{designationType}#{language}` (when single)

- `{designationType}` - The designation type (e.g., "display", "definition")
- `{language}` - The language code (e.g., "en", "ru", "ru-RU"). Can be empty string
- `{order}` - **Optional** sequential number starting from 1. Defaults to 1 when suffix is omitted

**Examples:**
- `display#en` - Single English display designation (order defaults to 1)
- `display#en##1`, `display#en##2` - Multiple English display designations
- `definition#en` - Single English definition designation (order defaults to 1)
- `definition#en##1`, `definition#en##2` - Multiple English definition designations
- `definition#ru` - Single Russian definition designation (order defaults to 1)

#### Legacy Format (Backward Compatibility)
- Simple column names that are mapped to designation properties via import configuration

### Property Columns

#### Simple Property Types

##### New Format (Recommended)
Format: `{propertyName}##{order}` (when multiple) or `{propertyName}` (when single)

- `{propertyName}` - The property name
- `{order}` - **Optional** sequential number starting from 1. Defaults to 1 when suffix is omitted

**Examples:**
- `itemWeight` - Single itemWeight value (order defaults to 1)
- `synonym` - Single synonym value (order defaults to 1)
- `synonym##1`, `synonym##2` - Multiple synonym values

##### Legacy Format (Backward Compatibility)
- Simple column names that are mapped to properties via import configuration
- Supports delimiter-separated values (e.g., "value1|value2|value3")

#### Coding Property Types

##### New Format (Recommended)
Format: `{csvPrefix}#code##{order}` and `{csvPrefix}#system##{order}` (when multiple) or `{csvPrefix}#code` and `{csvPrefix}#system` (when single)

- `{csvPrefix}` - A **file-local label** used only to pair code and system columns that belong together (same prefix, same order). It **does not** have to match the TermX **entity property** name chosen in the import mapping UI/API.
- `code` or `system` - Indicates whether this column contains the code or system
- `{order}` - **Optional** sequential number starting from 1. Defaults to 1 when suffix is omitted

**Important**: Code and system columns must be paired. The import processor:
- Identifies columns with `#code` or `#system` suffix as coding property columns
- Groups them by **CSV prefix** and order (so `type#code##1` pairs with `type#system##1`)
- Emits each combined coding value under the **mapped** property name from import configuration (e.g. CSV `type#…` columns may map to entity property `flag`)
- Creates a single coding value from each code/system pair

**Examples:**
- `type#code`, `type#system` - Single type coding value (order defaults to 1 for both)
- `type#code##1`, `type#system##1`, `type#code##2`, `type#system##2` - Multiple type coding values

**Processing Logic:**
1. Columns with `#code` or `#system` are identified as coding property columns
2. They are grouped by **CSV prefix** (the segment before `#code` / `#system`) and order
3. For each order, if both code and system are present, a coding value is created and assigned to the **mapped** TermX entity property name from the import request (not necessarily the CSV prefix)
4. If only code or only system is present, the value is stored as-is (may cause validation errors)

##### Legacy Format (Backward Compatibility)
- Single column containing code value (system may be specified via property configuration)

## Column Parsing Logic

### Designation Column Parsing

The import processor uses the following logic to parse designation columns:

1. **Check for order suffix**: If column contains `##`, extract the order number
2. **Default order**: If no suffix, order defaults to 1
3. **Split type and language**: Split by `#` to extract designation type and language
4. **Create designation property**: Create a property value with:
   - Property name: designation type
   - Property type: "designation"
   - Language: extracted language
   - Value: cell value
   - Order: extracted or defaulted order

### Property Column Parsing

The import processor uses the following logic to parse property columns:

1. **Check for coding suffix**: If column contains `#code` or `#system`, treat as coding property
2. **Check for order suffix**: If column contains `##`, extract the order number
3. **Default order**: If no suffix, order defaults to 1
4. **Extract CSV prefix for pairing**: Remove `#code`, `#system`, and `##{order}` suffixes to obtain the file-local prefix used to group pairs
5. **Group coding pairs**: For coding properties, group code and system columns by that CSV prefix and order; **output** property values use the configured mapped entity property name
6. **Create property values**: 
   - For coding: Create single value from code/system pair
   - For simple types: Create value(s) based on delimiter (if configured)

### Column Processing Order

The import processor processes columns in the following order to prevent false matches:

1. **Coding property columns first**: Columns containing `#code` or `#system` are identified and processed as property columns
2. **Designation columns second**: Columns matching designation pattern (`{type}#{language}`) are processed as designations
3. **Simple property columns last**: Remaining columns are processed as simple properties

This order ensures that `type#code##1` is correctly identified as a coding property column, not a designation column.

## Data Transformation

### Designation Values
- **Value**: Cell value is used as-is as the designation name
- **Empty cells**: Skipped (no designation created)
- **Multiple values**: Each order position creates a separate designation

### Simple Property Values

#### String
- **Value**: Cell value is used as-is
- **Delimiter support**: If property delimiter is configured, values are split and multiple property values are created
  - **Maximum delimiter length**: 3 characters
  - **Applies to**: Coding and string datatypes only
  - **Processing**: Split string into parts, trim each part, validate each part separately, create property value for every part
- **Empty cells**: Skipped

#### Integer
- **Value**: Parsed as `Integer.valueOf(cellValue)`
- **Empty cells**: Skipped
- **Invalid values**: May cause import errors

#### Decimal
- **Value**: Parsed as `Double.valueOf(cellValue)`
- **Empty cells**: Skipped
- **Invalid values**: May cause import errors

#### Boolean
- **Value**: Parsed as `true` if cell value is "1" or "true" (case-insensitive), otherwise `false`
- **Auto-detection**: If column has only values "false/true" or "1/0", the data type is automatically set to 'boolean'
- **Empty cells**: Skipped

#### DateTime
- **Value**: Parsed using the configured date format (`propertyTypeFormat`)
- **Supported formats**: 
  - `YYYY-MM-DD` (e.g., "2024-01-15")
  - `DD.MM.YY` (e.g., "15.01.24")
  - `DD.MM.YYYY` (e.g., "15.01.2024")
  - `MM/DD/YYYY` (e.g., "01/15/2024")
- **Format detection**: If column values match any supported date format, the type is automatically set to 'date' and the detected format is applied
- **Format selection**: Format can be explicitly selected in property configuration for date type properties
- **Format**: Uses `SimpleDateFormat` with strict parsing
- **Empty cells**: Skipped
- **Invalid values**: Returns `null` (may cause validation errors)

### Coding Property Values

#### New Format
- **Code column**: Contains the code value
- **System column**: Contains the code system URI
- **Pairing**: Code and system columns with the same property name and order are paired
- **Result**: Creates a single `EntityPropertyValue` with type `coding` and value `Map.of("code", codeValue, "codeSystem", systemValue)`
- **Missing pairs**: If only code or only system is present, the value is stored as-is (may cause validation errors)

#### Legacy Format
- **Single column**: Contains code value
- **System**: May be specified via property configuration (`propertyCodeSystem`)

## Import Configuration

### Required Properties

The import request must include at least one of the following identifier properties:
- `concept-code`: Maps to the `code` column (required for concept identification)
- `hierarchical-concept`: Alternative identifier for hierarchical concepts

At least one designation property must be configured:
- A property with `propertyType = "designation"` and a non-empty `language`

### Property Configuration

Each property in the import request includes:
- `columnName`: The exact column name in the file (immutable, from file header)
- `propertyName`: The property name in the CodeSystem (editable select from list of CodeSystem properties, defaults to `columnName` if not specified)
- `propertyType`: The property type (`string`, `integer`, `decimal`, `bool`, `dateTime`, `coding`, `designation`)
  - Selectable values: 'text' (string), 'integer', 'date' (dateTime), 'boolean' (bool), 'coding'
- `propertyTypeFormat`: Format string for dateTime properties
  - Selectable formats: `YYYY-MM-DD`, `DD.MM.YY`, `DD.MM.YYYY`, `MM/DD/YYYY`
  - Shown only for date type properties
- `propertyCodeSystem`: Code system URI for coding properties (legacy format)
- `propertyDelimiter`: Delimiter for splitting multiple values (e.g., "|")
  - Maximum 3 characters
  - Shown for Coding and string datatypes only
  - When specified: split string into parts, trim parts, validate each separately, create property for every part
- `language`: Language code for designation properties
  - Select from 'languages' value set
  - Required for designation type properties
- `preferred`: Whether this property is preferred (only one identifier property can be preferred)
- `import`: Checkbox indicating whether to import this column (defaults to true if column has at least one non-empty value)

### Auto Concept Order

If `autoConceptOrder` is enabled:
- Concepts are assigned an order based on their row position: `(rowIndex + 1) * 10`
- A `conceptOrder` property is automatically added to each concept

## Column Auto-Detection

The import system provides automatic detection and mapping for certain column patterns:

### Identifier Detection

If a column name matches any of the following patterns, it is automatically mapped to the "identifier" (`concept-code`) property:
- `id`
- `code`
- `identifier`
- `kood` (Estonian for "code")

**Note**: Only one identifier property is allowed per import.

### Date Format Detection

If column values match any supported date format, the system automatically:
- Sets the data type to 'date'
- Sets the detected format as the `propertyTypeFormat`

Supported date formats for auto-detection:
- `YYYY-MM-DD`
- `DD.MM.YY`
- `DD.MM.YYYY`
- `MM/DD/YYYY`

### Boolean Detection

If a column contains only values matching boolean patterns, the system automatically sets the data type to 'boolean':
- `true` / `false` (case-insensitive)
- `1` / `0`

### Import Flag Auto-Detection

The `import` flag (whether to import a column) is automatically set based on column content:
- **true**: If the column has at least one non-empty value
- **false**: If the column is completely empty

### Processing Rules

- Properties are added to the code system from the "KTS property" (or "CSV column" if "KTS property" is missing) with `import=true`
- Only one identifier property is allowed per import configuration

## Validation

### Column Validation
- **Missing columns**: If a configured property column is missing from the file, an exception is thrown (ApiError.TE712)
- **Missing identifier**: If no identifier property is found, an exception is thrown (ApiError.TE722)
- **Multiple preferred identifiers**: If more than one identifier property is marked as preferred, an exception is thrown (ApiError.TE707)

### Data Validation
- **Missing property type**: If a property has no type specified, an exception is thrown (ApiError.TE706)
- **Missing designation language**: If a designation property has no language, an exception is thrown (ApiError.TE728)
- **Duplicate concepts**: If multiple rows have the same concept code, an exception is thrown (ApiError.TE738)
- **Missing concept codes**: If a row has no concept code, an exception is thrown (ApiError.TE722)

## Error Handling

### Column Errors
- **TE712**: Column not found in file
  - **Message**: "Column '{{column}}' not found in file"
  - **When**: A configured property column is missing from the file headers

### Configuration Errors
- **TE706**: Property type not specified
  - **Message**: "Property type not specified for property '{{propertyName}}'"
  - **When**: A property in the import configuration has no `propertyType`

- **TE707**: Multiple preferred identifiers
  - **Message**: "Multiple preferred identifier properties found"
  - **When**: More than one identifier property is marked as preferred

- **TE721**: No designation property
  - **Message**: "At least one designation property is required"
  - **When**: No property with `propertyType = "designation"` is configured

- **TE722**: Missing identifier
  - **Message**: "At least one identifier property (concept-code or hierarchical-concept) is required"
  - **When**: No identifier property is configured or no concept code is found in a row

- **TE728**: Missing designation language
  - **Message**: "Designation property must have a language specified"
  - **When**: A designation property has no `language` configured

### Data Errors
- **TE738**: Duplicate concepts
  - **Message**: "Multiple rows found for the same concept code"
  - **When**: Multiple rows have the same concept code after grouping

## Examples

### Example 1: Simple Import with New Format

**File (CSV):**
```csv
code,display#en,itemWeight,synonym##1,synonym##2
a1,A1,10,x,Y
b1,B1,,g1,g2
```

**Configuration:**
- `code` → `concept-code` (identifier, preferred)
- `display#en` → `display` (designation, language: "en")
- `itemWeight` → `itemWeight` (property, type: `decimal`)
- `synonym##1`, `synonym##2` → `synonym` (property, type: `string`)

**Result:**
- Concept `a1`: display "A1" (en), itemWeight=10, synonyms=["x", "Y"]
- Concept `b1`: display "B1" (en), synonyms=["g1", "g2"]

### Example 2: Import with Coding Properties

**File (CSV):**
```csv
code,display#en,type#code##1,type#system##1,type#code##2,type#system##2
a1,A1,AdverseEvent,http://hl7.org/fhir/fhir-types,Age,http://hl7.org/fhir/fhir-types
b1,B1,Account,http://hl7.org/fhir/fhir-types,,
```

**Configuration:**
- `code` → `concept-code` (identifier, preferred)
- `display#en` → `display` (designation, language: "en")
- `type#code##1`, `type#system##1` → `type` (property, type: `coding`)
- `type#code##2`, `type#system##2` → `type` (property, type: `coding`)

The same CSV headers can be mapped to a different entity property name (e.g. all four columns → `flag`); the `type` segment in the file is only used to pair columns, not as the TermX property id.

**Result:**
- Concept `a1`: 
  - display "A1" (en)
  - type=[{code: "AdverseEvent", codeSystem: "http://hl7.org/fhir/fhir-types"}, {code: "Age", codeSystem: "http://hl7.org/fhir/fhir-types"}]
- Concept `b1`:
  - display "B1" (en)
  - type=[{code: "Account", codeSystem: "http://hl7.org/fhir/fhir-types"}]

### Example 3: Import with Multiple Designations

**File (CSV):**
```csv
code,display#en,definition#en,definition#en##2,definition#ru
b1,B1,bar-bar,bar,бар
```

**Configuration:**
- `code` → `concept-code` (identifier, preferred)
- `display#en` → `display` (designation, language: "en")
- `definition#en`, `definition#en##2` → `definition` (designation, language: "en")
- `definition#ru` → `definition` (designation, language: "ru")

**Result:**
- Concept `b1`:
  - display "B1" (en)
  - definitions=["bar-bar" (en), "bar" (en), "бар" (ru)]

### Example 4: Import with Optional Order Suffix (Single Values)

**File (CSV):**
```csv
code,display#en,itemWeight,synonym,type#code,type#system
a1,A1,10,x,AdverseEvent,http://hl7.org/fhir/fhir-types
```

**Configuration:**
- `code` → `concept-code` (identifier, preferred)
- `display#en` → `display` (designation, language: "en")
- `itemWeight` → `itemWeight` (property, type: `decimal`)
- `synonym` → `synonym` (property, type: `string`)
- `type#code`, `type#system` → `type` (property, type: `coding`)

**Result:**
- Concept `a1`:
  - display "A1" (en)
  - itemWeight=10
  - synonym="x"
  - type={code: "AdverseEvent", codeSystem: "http://hl7.org/fhir/fhir-types"}

**Note**: Since there's only one value for each property, the order suffix is omitted. The import processor defaults to order 1.

## Backward Compatibility

The import processor maintains backward compatibility with legacy formats:

1. **Legacy designation columns**: Simple column names mapped to designations via configuration
2. **Legacy property columns**: Simple column names with delimiter-separated values
3. **Legacy coding columns**: Single column with code value, system from configuration

The processor automatically detects the format based on column names:
- New format: Contains `#` or `##` patterns
- Legacy format: Simple column names

## Implementation Details

### Key Classes
- `CodeSystemFileImportProcessor`: Main import processor
- `FileParserFactory`: Factory for creating file parsers (CSV, TSV, XLSX)
- `CodeSystemFileImportRequest`: Import request configuration
- `CodeSystemFileImportResult`: Import result with entities and properties

### Key Methods
- `process()`: Main processing method that parses file and creates entities
- `parseNewFormatColumn()`: Parses property columns in new format
- `parseDesignationColumn()`: Parses designation columns in new format
- `mapPropValue()`: Maps property values with type transformation
- `transformPropertyValue()`: Transforms string values to appropriate types

### Column Parsing Classes
- `ColumnParseResult`: Result of parsing a property column (propertyName, order, isCoding, isSystem)
- `DesignationParseResult`: Result of parsing a designation column (type, language, order)
- `CodeSystemPair`: Helper class for pairing code and system values

### Processing Flow

1. **Validation**: Validate import configuration (required properties, types, etc.)
2. **File Parsing**: Parse file using appropriate parser (CSV/TSV/XLSX)
3. **Header Extraction**: Extract column headers from first row
4. **Row Processing**: For each row:
   - Create entity map
   - Process coding property columns (identify and pair code/system)
   - Process designation columns (parse type#language format)
   - Process simple property columns
   - Extract identifier (concept code)
   - Add auto concept order (if enabled)
5. **Grouping**: Group entities by concept code
6. **Validation**: Validate grouped entities (no duplicates, all have codes)
7. **Property Extraction**: Extract unique properties from configuration
8. **Result Creation**: Create import result with entities and properties

## Performance Considerations

- File parsing is stream-based for large files
- Entities are grouped in memory (consider memory limits for very large imports)
- Validation occurs after all rows are processed

## Migration from Legacy Format

When migrating from legacy format to new format:

1. **Update column names**: Rename columns to new format (e.g., `synonym` → `synonym##1`, `synonym##2`)
2. **Update coding columns**: Split single coding column into `property#code` and `property#system` columns
3. **Update designation columns**: Use `type#language` format instead of simple column names
4. **Update import configuration**: Map new column names to properties
5. **Test import**: Verify that all data is imported correctly

The import processor supports both formats simultaneously, so migration can be gradual.
