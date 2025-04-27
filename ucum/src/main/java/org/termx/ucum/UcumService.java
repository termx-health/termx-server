package org.termx.ucum;

import org.fhir.ucum.Decimal;

public interface UcumService {
    /**
     * Check if the provided unit string is a valid UCUM unit.
     *
     * @param unit the unit string (e.g., "mg", "L/min")
     * @return true if valid; false otherwise.
     */
    boolean isUnitValid(String unit);

    /**
     * Convert a numeric value from one UCUM unit to another.
     * @param value the numeric value to convert
     * @param sourceUnit the unit of the input value
     * @param targetUnit the unit to convert to
     * @return the converted value
     * @throws Exception if conversion fails or if any unit is invalid.
     */
    Decimal convert(Decimal value, String sourceUnit, String targetUnit) throws Exception;

    /**
     * Analyse a UCUM code and return its human-readable description.
     *
     * @param unit the UCUM code to analyse (e.g., "10.uN.s/(cm5.m2)")
     * @return a description of the UCUM code
     * @throws Exception if analysis fails
     */
    String analyse(String unit) throws Exception;

    /**
     * Retrieve details about the UCUM version, such as version number and release date.
     *
     * @return an object containing UCUM version details
     * @throws Exception if retrieval fails
     */
    Object getUcumVersionDetails() throws Exception;

    /**
     * Get the canonical representation for a given UCUM unit code.
     *
     * @param unit the UCUM unit code to canonicalize (e.g., "mg", "L/min")
     * @return the canonical unit string
     * @throws Exception if canonicalization fails or the unit is invalid
     */
    String getCanonicalUnits(String unit) throws Exception;

    /**
     * Fetch the list of UCUM components available in the model.
     *
     * @return an object representing UCUM components
     * @throws Exception if retrieval fails
     */
    Object getUcumComponents() throws Exception;

    /**
     * Search for UCUM components matching the provided text.
     *
     * @param text text to search within UCUM component properties
     * @return an object containing matching UCUM component properties
     * @throws Exception if search fails
     */
    Object searchComponent(String text) throws Exception;
}
