package org.termx.ucum.service;

import org.fhir.ucum.*;
import org.termx.ucum.dto.BaseUnitDto;
import org.termx.ucum.dto.DefinedUnitDto;
import org.termx.ucum.dto.PrefixDto;
import org.termx.ucum.dto.UcumVersionDto;
import org.termx.ucum.dto.AnalyseResponseDto;
import org.termx.ucum.dto.CanonicaliseResponseDto;
import org.termx.ucum.dto.ConvertResponseDto;
import org.termx.ucum.dto.ValidateResponseDto;

import java.math.BigDecimal;
import java.util.List;

public interface UcumService {

    /**
     * Retrieves details about the UCUM specification version used by the service,
     * including version number and release metadata.
     *
     * @return a DTO containing UCUM version information
     */
    UcumVersionDto getUcumVersionDetails();

    /**
     * Validates whether the given UCUM code is syntactically and semantically correct
     * according to the UCUM standard.
     *
     * @param code the UCUM unit code to validate
     * @return a DTO containing validation result and any error messages
     */
    ValidateResponseDto validate(String code);

    /**
     * Parses and analyses the provided UCUM unit expression, returning its structural breakdown.
     *
     * @param code the UCUM unit expression to analyse
     * @return a DTO with parsed analysis details
     * @throws UcumException if the unit expression analysis fails
     */
    AnalyseResponseDto analyse(String code) throws UcumException;

    /**
     * Converts a numerical value from one UCUM unit to another.
     *
     * @param value the numerical quantity to convert
     * @param sourceCode the UCUM unit code of the source unit
     * @param targetCode the UCUM unit code of the target unit
     * @return a DTO containing the converted value
     * @throws UcumException if conversion fails
     */
    ConvertResponseDto convert(BigDecimal value, String sourceCode, String targetCode) throws UcumException;

    /**
     * Computes the canonical form of the specified UCUM unit expression.
     *
     * @param code the UCUM unit code or expression to canonicalise
     * @return a DTO with the canonical unit representation
     * @throws UcumException if the expression cannot be canonicalised
     */
    CanonicaliseResponseDto getCanonicalUnits(String code) throws UcumException;

    /**
     * Retrieves all primitive base units defined in the UCUM standard.
     *
     * @return a list of DTOs for each base unit
     */
    List<BaseUnitDto> getBaseUnits();

    /**
     * Looks up a single base unit by its UCUM code.
     *
     * @param code the code of the base unit
     * @return a DTO representing the base unit details
     */
    BaseUnitDto getBaseUnitByCode(String code);

    /**
     * Retrieves all defined (derived or special) units available in the UCUM specification.
     *
     * @return a list of DTOs for each defined unit
     */
    List<DefinedUnitDto> getDefinedUnits();

    /**
     * Looks up a defined unit by its UCUM code.
     *
     * @param code the code of the defined unit
     * @return a DTO representing the defined unit details
     */
    DefinedUnitDto getDefinedUnitByCode(String code);

    /**
     * Retrieves all available UCUM prefixes (e.g., milli, kilo).
     *
     * @return a list of DTOs for each prefix
     */
    List<PrefixDto> getPrefixes();

    /**
     * Looks up a UCUM prefix by its code.
     *
     * @param code the prefix code (e.g., "m" for milli)
     * @return a DTO representing the prefix details
     */
    PrefixDto getPrefixByCode(String code);
}
