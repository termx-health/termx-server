package org.termx.ucum;

import lombok.extern.slf4j.Slf4j;
import org.fhir.ucum.Decimal;
import org.fhir.ucum.UcumEssenceService;
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
    public boolean isCodeValid(String code) {
        String errorMessage = ucumService.validate(code);
        return errorMessage == null;
    }

    @Override
    public Decimal convert(Decimal value, String sourceCode, String targetCode) throws Exception {
        try {
            return ucumService.convert(value, sourceCode, targetCode);
        } catch (Exception e) {
            throw new Exception(String.format("Error converting UCUM code: %s", e.getMessage()));
        }
    }

    @Override
    public String analyse(String code) throws Exception {
        try {
            return ucumService.analyse(code);
        } catch (Exception e) {
            throw new Exception(String.format("Error analysing UCUM code: '%s': %s", code, e.getMessage()));
        }
    }

    @Override
    public Object getUcumVersionDetails() throws Exception {
        try {
            return ucumService.ucumIdentification();
        } catch (Exception e) {
            throw new Exception(String.format("Error getting UCUM version details: %s", e.getMessage()));
        }
    }

    @Override
    public String getCanonicalUnits(String code) throws Exception {
        try {
            return ucumService.getCanonicalUnits(code);
        } catch (Exception e) {
            throw new Exception(String.format("Error getting canonical units for: '%s': %s", code, e.getMessage()));
        }
    }

    @Override
    public Object getUcumComponents() throws Exception {
        try {
            return ucumService.getModel();
        } catch (Exception e) {
            throw new Exception(String.format("Error getting UCUM components: %s", e.getMessage()));
        }
    }

    @Override
    public Object searchComponents(String text) throws Exception {
        try {
            return ucumService.search(null, text, false);
        } catch (Exception e) {
            throw new Exception(String.format("Error searching UCUM components: %s", e.getMessage()));
        }
    }
}
