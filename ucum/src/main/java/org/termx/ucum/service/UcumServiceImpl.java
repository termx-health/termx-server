package org.termx.ucum.service;

import lombok.extern.slf4j.Slf4j;
import org.fhir.ucum.*;
import org.fhir.ucum.UcumService.UcumVersionDetails;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.termx.ucum.dto.BaseUnitDto;
import org.termx.ucum.dto.DefinedUnitDto;
import org.termx.ucum.dto.PrefixDto;
import org.termx.ucum.dto.UcumVersionDto;
import org.termx.ucum.mapper.BaseUnitMapper;
import org.termx.ucum.mapper.DefinedUnitMapper;
import org.termx.ucum.mapper.PrefixMapper;
import org.termx.ucum.mapper.UcumVersionMapper;
import org.termx.ucum.dto.AnalyseResponseDto;
import org.termx.ucum.dto.CanonicaliseResponseDto;
import org.termx.ucum.dto.ConvertResponseDto;
import org.termx.ucum.dto.ValidateResponseDto;
import org.termx.ucum.mapper.AnalyseResponseMapper;
import org.termx.ucum.mapper.CanonicaliseResponseMapper;
import org.termx.ucum.mapper.ConvertResponseMapper;
import org.termx.ucum.mapper.ValidateResponseMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UcumServiceImpl implements UcumService {
    private final UcumVersionMapper versionMapper;
    private final ValidateResponseMapper validateMapper;
    private final AnalyseResponseMapper analyseMapper;
    private final ConvertResponseMapper convertMapper;
    private final CanonicaliseResponseMapper canonicaliseMapper;
    private final DefinedUnitMapper definedUnitMapper;
    private final BaseUnitMapper baseUnitMapper;
    private final PrefixMapper prefixMapper;

    private final UcumEssenceService ucumService;

    @Autowired
    public UcumServiceImpl(
            ValidateResponseMapper validateMapper,
            UcumVersionMapper versionMapper,
            AnalyseResponseMapper analyseMapper,
            ConvertResponseMapper convertMapper,
            CanonicaliseResponseMapper canonicaliseMapper,
            DefinedUnitMapper definedUnitMapper,
            BaseUnitMapper baseUnitMapper,
            PrefixMapper prefixMapper) {
        this.validateMapper = validateMapper;
        this.versionMapper = versionMapper;
        this.analyseMapper = analyseMapper;
        this.convertMapper = convertMapper;
        this.canonicaliseMapper = canonicaliseMapper;
        this.definedUnitMapper = definedUnitMapper;
        this.baseUnitMapper = baseUnitMapper;
        this.prefixMapper = prefixMapper;
        this.ucumService = initUcumService();
    }

    private UcumEssenceService initUcumService() {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("ucum-essence.xml");
        if (inputStream == null) {
            throw new IllegalStateException("Could not find ucum-essence.xml in classpath");
        }
        try {
            return new UcumEssenceService(inputStream);
        } catch (Exception e) {
            throw new IllegalStateException("Error loading ucum-essence.xml", e);
        }
    }

    @Override
    public UcumVersionDto getUcumVersionDetails() {
        UcumVersionDetails versionDetails = ucumService.ucumIdentification();
        return versionMapper.toDto(versionDetails);
    }

    @Override
    public ValidateResponseDto validate(String code) {
        String errorMessage = ucumService.validate(code);
        return validateMapper.toDto(errorMessage);
    }

    @Override
    public AnalyseResponseDto analyse(String code) throws UcumException {
        String analysisResult = ucumService.analyse(code);
        return analyseMapper.toDto(analysisResult);
    }

    @Override
    public ConvertResponseDto convert(BigDecimal value, String sourceCode, String targetCode) throws UcumException {
        Decimal conversionResult = ucumService.convert(new Decimal(value.toString()), sourceCode, targetCode);
        return convertMapper.toDto(conversionResult);
    }

    @Override
    public CanonicaliseResponseDto getCanonicalUnits(String code) throws UcumException {
        String canonicalExpression = ucumService.getCanonicalUnits(code);
        return canonicaliseMapper.toDto(canonicalExpression);
    }

    @Override
    public List<BaseUnitDto> getBaseUnits() {
        List<BaseUnit> baseUnits = ucumService.getModel().getBaseUnits();
        return baseUnits.stream()
                .map(baseUnitMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public BaseUnitDto getBaseUnitByCode(String code) {
        BaseUnit baseUnit = ucumService.getModel().getBaseUnit(code);
        return baseUnitMapper.toDto(baseUnit);
    }

    @Override
    public List<DefinedUnitDto> getDefinedUnits() {
        List<DefinedUnit> definedUnits = ucumService.getModel().getDefinedUnits();
        return definedUnits.stream()
                    .map(definedUnitMapper::toDto)
                    .collect(Collectors.toList());
    }

    @Override
    public DefinedUnitDto getDefinedUnitByCode(String code) {
        List<DefinedUnit> definedUnits = ucumService.getModel().getDefinedUnits();
        for (DefinedUnit unit : definedUnits) {
            if (unit.getCode().equals(code))
                return definedUnitMapper.toDto(unit);
        }
        return null;
    }

    @Override
    public List<PrefixDto> getPrefixes() {
        List<Prefix> prefixes = ucumService.getModel().getPrefixes();
        return prefixes.stream()
                .map(prefixMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public PrefixDto getPrefixByCode(String code) {
        List<Prefix> prefixes = ucumService.getModel().getPrefixes();
        for (Prefix prefix : prefixes) {
            if (prefix.getCode().equals(code))
                return prefixMapper.toDto(prefix);
        }
        return null;
    }

    @Override
    public List<Concept> searchComponents(String kind, String text) throws Exception {
        try {
            ConceptKind conceptKind = Optional.ofNullable(kind)
                .map(ConceptKind::valueOf)
                .orElse(null);
            return ucumService.search(conceptKind, text, false);
        } catch (Exception e) {
            throw new Exception(String.format("Error searching UCUM components: %s", e.getMessage()));
        }
    }
}
