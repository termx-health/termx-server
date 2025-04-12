package org.termx.ucum;

import org.fhir.ucum.Decimal;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;

import java.io.InputStream;

public class UcumServiceImpl implements UcumService {

    private final UcumEssenceService ucumService;

    public UcumServiceImpl() throws UcumException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("ucum-essence.xml");
        if (inputStream == null) {
            throw new UcumException("Could not find ucum-essence.xml in classpath");
        }
        this.ucumService = new UcumEssenceService(inputStream);
    }

    @Override
    public boolean isValidUnit(String unit) {
        try {
            ucumService.validate(unit);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Decimal convert(Decimal value, String sourceUnit, String targetUnit) throws Exception {
        if (isValidUnit(sourceUnit)) {
            throw new UcumException("Invalid source unit: " + sourceUnit);
        }
        if (isValidUnit(targetUnit)) {
            throw new UcumException("Invalid target unit: " + targetUnit);
        }
        return ucumService.convert(value, sourceUnit, targetUnit);
    }
}
