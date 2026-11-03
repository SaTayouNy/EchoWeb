package org.example.echo.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import static org.assertj.core.api.Assertions.assertThat;

class RatingCalculatorTest {
    private final RatingCalculator calculator = new RatingCalculator();

    @Test
    void approvedExampleKeepsUnroundedMultiplierUntilFinalScore() {
        var score = calculate(33, 9);
        assertThat(score.formQuality()).isEqualByComparingTo("8.25");
        assertThat(score.baseScore()).isEqualByComparingTo("82.5");
        assertThat(score.impressionMultiplier()).isEqualByComparingTo("0.933333333333");
        assertThat(score.finalScore()).isEqualTo(77);
    }

    @Test
    void endpointsAndHalfTiesFollowApprovedScale() {
        assertThat(calculate(4, 1).finalScore()).isEqualTo(4);
        assertThat(calculate(40, 1).finalScore()).isEqualTo(40);
        assertThat(calculate(4, 10).finalScore()).isEqualTo(10);
        assertThat(calculate(40, 10).finalScore()).isEqualTo(100);
        assertThat(calculate(33, 10).finalScore()).isEqualTo(83);
    }

    @Test
    void allReachableSumsAndImpressionsAgreeWithExactRationalAndAreMonotone() {
        for (int sum = 4; sum <= 40; sum++) {
            int previous = -1;
            for (int impression = 1; impression <= 10; impression++) {
                // Algebraically the approved formula is sum*(impression+5)/6.
                int expected = BigDecimal.valueOf((long) sum * (impression + 5))
                    .divide(BigDecimal.valueOf(6), 0, RoundingMode.HALF_UP).intValueExact();
                int actual = calculate(sum, impression).finalScore();
                assertThat(actual).isEqualTo(expected).isBetween(4, 100).isGreaterThanOrEqualTo(previous);
                previous = actual;
            }
        }
    }

    private org.example.echo.model.RatingModels.Calculation calculate(int sum, int impression) {
        return calculator.calculate(sum, impression, new BigDecimal("0.4"), BigDecimal.ONE, RoundingMode.HALF_UP);
    }
}
