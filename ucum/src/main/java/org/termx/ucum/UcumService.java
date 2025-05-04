package org.termx.ucum;

import org.fhir.ucum.Decimal;

public interface UcumService {
    /**
     * Check if the provided UCUM code is valid.
     *
     * @param code the UCUM code string (e.g., "mg", "L/min")
     * @return true if valid; false otherwise.
     */
    boolean isCodeValid(String code);

    /**
     * Convert a numeric value from one UCUM code to another.
     * @param value the numeric value to convert
     * @param sourceCode the UCUM code of the input value
     * @param targetCode the UCUM code to convert to
     * @return the converted value
     * @throws Exception if conversion fails or if any UCUM code is invalid.
     */
    Decimal convert(Decimal value, String sourceCode, String targetCode) throws Exception;

    /**
     * Analyse a UCUM code and return its human-readable description.
     *
     * @param code the UCUM code to analyse (e.g., "10.uN.s/(cm5.m2)")
     * @return a description of the UCUM code
     * @throws Exception if analysis fails
     */
    String analyse(String code) throws Exception;

    /**
     * Retrieve details about the UCUM version, such as version number and release date.
     *
     * @return an object containing UCUM version details
     * @throws Exception if retrieval fails
     */
    Object getUcumVersionDetails() throws Exception;

    /**
     * Get the canonical representation for a given UCUM code.
     *
     * @param code the UCUM code to canonicalize (e.g., "mg", "L/min")
     * @return the canonical unit representation string
     * @throws Exception if canonicalization fails or the code is invalid
     */
    String getCanonicalUnits(String code) throws Exception;

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
     * @param text text to search within UCUM components
     * @return an object containing matching UCUM components
     * @throws Exception if search fails
     */
    Object searchComponents(String kind, String text) throws Exception;
}
