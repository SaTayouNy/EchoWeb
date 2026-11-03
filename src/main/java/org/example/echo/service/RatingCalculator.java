package org.example.echo.service;

import org.example.echo.entity.RatingParameterEntity;
import org.example.echo.entity.RatingSchemeEntity;
import org.example.echo.exception.ApiException;
import org.example.echo.model.RatingModels;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RatingCalculator {
    public RatingModels.Calculation calculate(RatingSchemeEntity scheme, List<RatingParameterEntity> definitions,
                                               Map<String, Integer> values) {
        if (!"FORM_IMPRESSION_LINEAR_V1".equals(scheme.getFormulaCode())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RATING_SCHEME_NOT_CONFIGURED", "Unsupported rating formula");
        }
        Set<String> expected = definitions.stream().map(RatingParameterEntity::getCode).collect(Collectors.toSet());
        if (values == null || !expected.equals(values.keySet())) {
            throw invalid("All scheme parameters must be supplied exactly once; unknown parameters are not allowed");
        }
        for (var definition : definitions) {
            Integer value = values.get(definition.getCode());
            if (value == null || value < definition.getMinimumValue() || value > definition.getMaximumValue()) {
                throw invalid("Parameter " + definition.getCode() + " must be an integer in range 1–10");
            }
        }
        var form = definitions.stream().filter(p -> "FORM".equals(p.getRole())).toList();
        var impression = definitions.stream().filter(p -> "IMPRESSION".equals(p.getRole())).toList();
        if (form.size() != 4 || impression.size() != 1 || definitions.size() != 5) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RATING_SCHEME_NOT_CONFIGURED", "Invalid rating scheme");
        }
        int sum = form.stream().mapToInt(p -> values.get(p.getCode())).sum();
        return calculate(sum, values.get(impression.getFirst().getCode()), scheme.getMinimumMultiplier(),
            scheme.getMaximumMultiplier(), RoundingMode.valueOf(scheme.getRoundingMode()));
    }

    /** Multiplier stays a rational numerator / 9 until final rounding, including exact half ties. */
    RatingModels.Calculation calculate(int formSum, int impression, BigDecimal minimum,
                                       BigDecimal maximum, RoundingMode roundingMode) {
        if (formSum < 4 || formSum > 40 || impression < 1 || impression > 10) throw invalid("Values out of range");
        BigDecimal quality = BigDecimal.valueOf(formSum).divide(BigDecimal.valueOf(4));
        BigDecimal base = quality.multiply(BigDecimal.TEN);
        BigDecimal denominator = BigDecimal.valueOf(9);
        BigDecimal numerator = minimum.multiply(denominator)
            .add(maximum.subtract(minimum).multiply(BigDecimal.valueOf(impression - 1)));
        int total = base.multiply(numerator).divide(denominator, 0, roundingMode).intValueExact();
        return new RatingModels.Calculation(quality, base,
            numerator.divide(denominator, 12, RoundingMode.HALF_UP).stripTrailingZeros(), Math.min(100, total));
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message);
    }
}
