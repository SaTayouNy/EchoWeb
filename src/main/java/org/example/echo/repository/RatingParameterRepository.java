package org.example.echo.repository;

import org.example.echo.entity.RatingParameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RatingParameterRepository extends JpaRepository<RatingParameterEntity, String> {
    List<RatingParameterEntity> findBySchemeVersionOrderByDisplayOrder(String version);
}
