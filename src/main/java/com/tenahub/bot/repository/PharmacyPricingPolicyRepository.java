package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyPricingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PharmacyPricingPolicyRepository extends JpaRepository<PharmacyPricingPolicy, Long> {
    Optional<PharmacyPricingPolicy> findByPharmacyId(Long pharmacyId);
}
