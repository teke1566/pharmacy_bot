package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.repository.PharmacyRegistrationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final PharmacyRegistrationRepository registrationRepository;
    private final PharmacyRepository pharmacyRepository;
    private final InventoryService inventoryService;

@Override
public Long register(String name,
                     String city,
                     String area,
                     String phone,
                     String medicines,
                     String openTime,
                     String closeTime,
                     Long telegramId) {

    PharmacyRegistration registration = registrationRepository
            .findTopByTelegramIdOrderByIdDesc(telegramId)
            .orElseGet(PharmacyRegistration::new);

    registration.setName(name);
    registration.setCity(city);
    registration.setArea(area);
    registration.setPhone(phone);
    registration.setMedicines(medicines);
    registration.setOpenTime(openTime);
    registration.setCloseTime(closeTime);
    registration.setTelegramId(telegramId);
    registration.setStatus("PENDING");

    PharmacyRegistration saved = registrationRepository.save(registration);
    return saved.getId();
}
    @Override
    public void saveLocation(Long telegramId, Double latitude, Double longitude) {
        Optional<PharmacyRegistration> optional =
                registrationRepository.findFirstByTelegramIdAndStatusOrderByIdDesc(telegramId, "PENDING");

        if (optional.isEmpty()) {
            return;
        }

        PharmacyRegistration reg = optional.get();
        reg.setLatitude(latitude);
        reg.setLongitude(longitude);

        registrationRepository.save(reg);
    }

    @Override
    public void saveLocationDetails(Long telegramId, String formattedAddress, String landmark, String plusCode) {
        Optional<PharmacyRegistration> optional =
                registrationRepository.findFirstByTelegramIdAndStatusOrderByIdDesc(telegramId, "PENDING");

        if (optional.isEmpty()) {
            return;
        }

        PharmacyRegistration reg = optional.get();
        reg.setFormattedAddress(formattedAddress);
        reg.setLandmark(landmark);
        reg.setPlusCode(plusCode);

        registrationRepository.save(reg);
    }
    

    @Override
    public Long saveLicense(Long telegramId, String fileId) {
        PharmacyRegistration reg =
                registrationRepository
                        .findTopByTelegramIdAndStatusOrderByIdDesc(telegramId, "PENDING")
                        .orElseThrow(() -> new RuntimeException("Registration not found"));

        reg.setLicenseFileId(fileId);
        registrationRepository.save(reg);

        return reg.getId();
    }

    @Override
    public Long approve(Long registrationId) {
        PharmacyRegistration reg =
                registrationRepository.findById(registrationId)
                        .orElseThrow(() -> new RuntimeException("Registration not found"));

        if ("APPROVED".equals(reg.getStatus()) || "REJECTED".equals(reg.getStatus())) {
            return reg.getTelegramId();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm");

        Pharmacy pharmacy = Pharmacy.builder()
                .name(reg.getName())
                .city(reg.getCity())
                .area(reg.getArea())
                .phone(reg.getPhone())
                .medicines(reg.getMedicines())
                .openTime(LocalTime.parse(reg.getOpenTime(), formatter))
                .closeTime(LocalTime.parse(reg.getCloseTime(), formatter))
                .latitude(reg.getLatitude())
                .longitude(reg.getLongitude())
                .formattedAddress(reg.getFormattedAddress())
                .landmark(reg.getLandmark())
                .plusCode(reg.getPlusCode())
                .telegramId(reg.getTelegramId())
                .licenseFileId(reg.getLicenseFileId())
                .approved(true)
                .rating(0.0)
                .build();

        pharmacyRepository.save(pharmacy);
        inventoryService.initializeInventoryFromMedicines(pharmacy.getId(), pharmacy.getMedicines());

        reg.setStatus("APPROVED");
        registrationRepository.save(reg);

        return reg.getTelegramId();
    }

    @Override
    public Long reject(Long registrationId) {
        PharmacyRegistration reg =
                registrationRepository.findById(registrationId)
                        .orElseThrow(() -> new RuntimeException("Registration not found"));

        if ("APPROVED".equals(reg.getStatus()) || "REJECTED".equals(reg.getStatus())) {
            return reg.getTelegramId();
        }

        reg.setStatus("REJECTED");
        registrationRepository.save(reg);

        return reg.getTelegramId();
    }

    @Override
    public void rejectWithReason(Long registrationId, String reason) {
        PharmacyRegistration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        reg.setStatus("REJECTED");
        reg.setRejectionReason(reason);

        registrationRepository.save(reg);
    }

    @Override
    public boolean exists(Long telegramId) {
        return registrationRepository
                .findFirstByTelegramIdAndStatusOrderByIdDesc(telegramId, "PENDING")
                .isPresent();
    }

    @Override
    public boolean licenseAlreadyUploaded(Long telegramId) {
        var reg = registrationRepository
                .findTopByTelegramIdAndStatusOrderByIdDesc(telegramId, "PENDING");

        if (reg.isEmpty()) {
            return false;
        }

        return reg.get().getLicenseFileId() != null;
    }

    @Override
    public boolean isProcessed(Long id) {
        return registrationRepository.findById(id)
                .map(reg -> !"PENDING".equals(reg.getStatus()))
                .orElse(false);
    }

    @Override
    public PharmacyRegistration getRegistration(Long id) {
        return registrationRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Registration not found"));
    }

    @Override
    public Long getApprovedPharmacyId(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Approved pharmacy not found"));

        return pharmacy.getId();
    }

    @Override
    public boolean isRegisteredPharmacy(Long telegramId) {
        return pharmacyRepository.existsByTelegramId(telegramId);
    }

    @Override
    public boolean existsById(Long id) {
        return registrationRepository.existsById(id);
    }

    @Override
    public PharmacyRegistration getLatestRejected(Long telegramId) {
        return registrationRepository
                .findFirstByTelegramIdAndStatusOrderByIdDesc(telegramId, "REJECTED")
                .orElse(null);
    }

@Override
public Long restartRejectedRegistration(Long telegramId) {
    PharmacyRegistration rejected = registrationRepository
            .findFirstByTelegramIdAndStatusOrderByIdDesc(telegramId, "REJECTED")
            .orElseThrow(() -> new RuntimeException("Rejected registration not found"));

    rejected.setStatus("PENDING");
    rejected.setRejectionReason(null);

    if (rejected.getLicenseFileId() != null && rejected.getLicenseFileId().isBlank()) {
        rejected.setLicenseFileId(null);
    }

    PharmacyRegistration saved = registrationRepository.save(rejected);
    return saved.getId();
}

    @Override
    public void deleteRegistration(Long id) {
        PharmacyRegistration reg = registrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        registrationRepository.delete(reg);
    }

    @Override
    public void deletePendingByTelegramId(Long telegramId) {
        registrationRepository
                .findFirstByTelegramIdAndStatusOrderByIdDesc(telegramId, "PENDING")
                .ifPresent(registrationRepository::delete);
    }

    @Override
    public void deleteRegistrationByTelegramId(Long telegramId) {
        List<PharmacyRegistration> registrations = registrationRepository.findAllByTelegramId(telegramId);

        if (!registrations.isEmpty()) {
            registrationRepository.deleteAll(registrations);
        }
    }

    @Override
    public int deleteInvalidPendingRegistrations() {
        List<PharmacyRegistration> pendingList =
                registrationRepository.findByStatusOrderByIdDesc("PENDING");

        List<PharmacyRegistration> invalidList = pendingList.stream()
                .filter(reg ->
                        isBlank(reg.getName()) ||
                        isBlank(reg.getCity()) ||
                        isBlank(reg.getArea()) ||
                        isBlank(reg.getPhone()) ||
                        isBlank(reg.getMedicines()) ||
                        isBlank(reg.getOpenTime()) ||
                        isBlank(reg.getCloseTime()) ||
                        reg.getLatitude() == null ||
                        reg.getLongitude() == null ||
                        isBlank(reg.getLicenseFileId()) ||
                        reg.getTelegramId() == null
                )
                .toList();

        if (!invalidList.isEmpty()) {
            registrationRepository.deleteAll(invalidList);
        }

        return invalidList.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}