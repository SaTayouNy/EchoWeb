package org.example.echo.repository;

import org.example.echo.entity.RatingSchemeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RatingSchemeRepository extends JpaRepository<RatingSchemeEntity, String> {
    List<RatingSchemeEntity> findByActiveTrue();
}
