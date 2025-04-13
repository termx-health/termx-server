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
     * Analyse a UCUM code and return its description.
     *
     * @param ucumCode the UCUM code to analyse
     * @return a string description of the UCUM code
     * @throws Exception if analysis fails.
     */
    String analyse(String ucumCode) throws Exception;
}
