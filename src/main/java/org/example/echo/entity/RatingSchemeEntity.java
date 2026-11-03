package org.example.echo.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;

/** Published schemes are immutable; new formulas require a new version. */
@Entity
@Immutable
@Table(name = "rating_schemes")
public class RatingSchemeEntity {
    @Id @Column(name = "scheme_version", length = 64)
    private String version;
    @Column(name = "formula_code", nullable = false, length = 64)
    private String formulaCode;
    @Column(nullable = false) private boolean active;
    @Column(name = "minimum_multiplier", nullable = false, precision = 12, scale = 8)
    private BigDecimal minimumMultiplier;
    @Column(name = "maximum_multiplier", nullable = false, precision = 12, scale = 8)
    private BigDecimal maximumMultiplier;
    @Column(name = "rounding_mode", nullable = false, length = 16)
    private String roundingMode;
    public String getVersion() { return version; }
    public String getFormulaCode() { return formulaCode; }
    public boolean isActive() { return active; }
    public BigDecimal getMinimumMultiplier() { return minimumMultiplier; }
    public BigDecimal getMaximumMultiplier() { return maximumMultiplier; }
    public String getRoundingMode() { return roundingMode; }
}
