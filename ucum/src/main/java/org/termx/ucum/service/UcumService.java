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

    UcumVersionDto getUcumVersionDetails();

    ValidateResponseDto validate(String code);

    AnalyseResponseDto analyse(String code) throws UcumException;

    ConvertResponseDto convert(BigDecimal value, String sourceCode, String targetCode) throws UcumException;

    CanonicaliseResponseDto getCanonicalUnits(String code) throws UcumException;

    List<BaseUnitDto> getBaseUnits();

    BaseUnitDto getBaseUnitByCode(String code);

    List<DefinedUnitDto> getDefinedUnits();

    DefinedUnitDto getDefinedUnitByCode(String code);

    List<PrefixDto> getPrefixes();

    PrefixDto getPrefixByCode(String code);

    List<Concept> searchComponents(String kind, String text) throws Exception;
}
