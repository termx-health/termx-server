package org.termx.ucum;

import lombok.extern.slf4j.Slf4j;
import org.fhir.ucum.Decimal;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
public class UcumServiceImpl implements UcumService {

    private final UcumEssenceService ucumService;

    public UcumServiceImpl() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("ucum-essence.xml");
        if (inputStream == null) {
            throw new Exception("Could not find ucum-essence.xml in classpath");
        }
        try {
            this.ucumService = new UcumEssenceService(inputStream);
        } catch (Exception e) {
            throw new Exception("Error loading ucum-essence.xml", e);
        }
    }

    @Override
    public boolean isUnitValid(String unit) {
        String errorMessage = ucumService.validate(unit);
        return errorMessage == null;
    }

    @Override
    public Decimal convert(Decimal value, String sourceUnit, String targetUnit) throws Exception {
        if (!isUnitValid(sourceUnit)) {
            throw new Exception("Invalid source unit: " + sourceUnit);
        }
        if (!isUnitValid(targetUnit)) {
            throw new Exception("Invalid target unit: " + targetUnit);
        }
        return ucumService.convert(value, sourceUnit, targetUnit);
    }

    @Override
    public String analyse(String ucumCode) throws Exception {
        try {
            return ucumService.analyse(ucumCode);
        } catch (UcumException e) {
            throw new Exception("Error analysing unit: " + ucumCode, e);
        }
    }
}
