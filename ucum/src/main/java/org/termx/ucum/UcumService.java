package org.termx.ucum;

import org.fhir.ucum.Decimal;

public interface UcumService {
    /**
     * Check if the provided unit string is a valid UCUM unit.
     * @param unit the unit string (e.g., "mg", "L/min")
     * @return true if valid; false otherwise.
     */
    boolean isValidUnit(String unit);

    /**
     * Convert a numeric value from one UCUM unit to another.
     * @param value the numeric value to convert
     * @param sourceUnit the unit of the input value
     * @param targetUnit the unit to convert to
     * @return the converted value
     * @throws Exception if conversion fails or if any unit is invalid.
     */
    Decimal convert(Decimal value, String sourceUnit, String targetUnit) throws Exception;
}
