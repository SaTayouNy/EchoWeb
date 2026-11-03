package org.example.echo.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "rating_parameters")
public class RatingParameterEntity {
    @Id @Column(name = "parameter_id", length = 128) private String id;
    @Column(name = "scheme_version", nullable = false, length = 64) private String schemeVersion;
    @Column(name = "parameter_code", nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 255) private String name;
    @Column(nullable = false, length = 2000) private String description;
    @Column(name = "parameter_role", nullable = false, length = 16) private String role;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "minimum_value", nullable = false) private int minimumValue;
    @Column(name = "maximum_value", nullable = false) private int maximumValue;
    public String getId() { return id; }
    public String getSchemeVersion() { return schemeVersion; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRole() { return role; }
    public int getDisplayOrder() { return displayOrder; }
    public int getMinimumValue() { return minimumValue; }
    public int getMaximumValue() { return maximumValue; }
}
