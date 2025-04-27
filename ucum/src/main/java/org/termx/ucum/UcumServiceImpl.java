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
    public String analyse(String unit) throws Exception {
        try {
            return ucumService.analyse(unit);
        } catch (UcumException e) {
            throw new Exception("Error analysing unit: " + unit, e);
        }
    }

    @Override
    public Object getUcumVersionDetails() throws Exception {
        try {
            return ucumService.ucumIdentification();
        } catch (Exception e) {
            throw new Exception("Error getting UCUM version details", e);
        }
    }

    @Override
    public String getCanonicalUnits(String unit) throws Exception {
        try {
            return ucumService.getCanonicalUnits(unit);
        } catch (Exception e) {
            throw new Exception("Error getting canonical units", e);
        }
    }

    @Override
    public Object getUcumComponents() throws Exception {
        try {
            return ucumService.getModel();
        } catch (Exception e) {
            throw new Exception("Error getting UCUM components", e);
        }
    }

    @Override
    public Object searchComponent(String text) throws Exception {
        try {
            return ucumService.search(null, text, false);
        } catch (Exception e) {
            throw new Exception("Error getting UCUM component properties", e);
        }
    }
}
