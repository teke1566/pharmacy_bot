package com.tenahub.bot.repository;

import com.tenahub.bot.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    Optional<Medicine> findByCanonicalName(String canonicalName);

    List<Medicine> findByActiveIngredientIgnoreCase(String activeIngredient);

    List<Medicine> findByCanonicalNameContainingIgnoreCase(String fragment);

    @Query("""
            select m from Medicine m
            where lower(m.canonicalName) = :canonical
               or lower(m.canonicalName) like concat('%', :term, '%')
               or lower(m.name) like concat('%', :term, '%')
            """)
    List<Medicine> searchByName(@Param("canonical") String canonical, @Param("term") String term);
}
