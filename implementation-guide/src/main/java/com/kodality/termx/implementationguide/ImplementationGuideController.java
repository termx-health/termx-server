package com.kodality.termx.implementationguide;

import io.micronaut.http.annotation.Controller;
import io.micronaut.validation.Validated;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@Controller("/implementation-guides")
public class ImplementationGuideController {
}
