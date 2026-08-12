package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.repository.PharmacyRegistrationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private PharmacyRegistrationRepository registrationRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private RegistrationServiceImpl service;

    @Test
    void register_createsPendingRegistration() {
        when(registrationRepository.findTopByTelegramIdOrderByIdDesc(77L)).thenReturn(Optional.empty());
        when(registrationRepository.save(any())).thenAnswer(inv -> {
            PharmacyRegistration registration = inv.getArgument(0);
            registration.setId(15L);
            return registration;
        });

        Long id = service.register("City Rx", "Addis", "Bole", "0911", "Panadol", "8:00", "20:00", 77L);

        assertEquals(15L, id);
        ArgumentCaptor<PharmacyRegistration> captor = ArgumentCaptor.forClass(PharmacyRegistration.class);
        verify(registrationRepository).save(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("paracetamol", captor.getValue().getMedicines());
        assertEquals(77L, captor.getValue().getTelegramId());
    }

    @Test
    void reject_marksPendingAsRejected() {
        PharmacyRegistration registration = PharmacyRegistration.builder()
                .id(15L)
                .telegramId(77L)
                .status("PENDING")
                .build();
        when(registrationRepository.findById(15L)).thenReturn(Optional.of(registration));

        Long telegramId = service.reject(15L);

        assertEquals(77L, telegramId);
        assertEquals("REJECTED", registration.getStatus());
        verify(registrationRepository).save(registration);
    }

    @Test
    void reject_doesNotChangeAlreadyApproved() {
        PharmacyRegistration registration = PharmacyRegistration.builder()
                .id(15L)
                .telegramId(77L)
                .status("APPROVED")
                .build();
        when(registrationRepository.findById(15L)).thenReturn(Optional.of(registration));

        Long telegramId = service.reject(15L);

        assertEquals(77L, telegramId);
        assertEquals("APPROVED", registration.getStatus());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void approve_createsPharmacyAndInventory() {
        PharmacyRegistration registration = PharmacyRegistration.builder()
                .id(15L)
                .name("City Rx")
                .city("Addis")
                .area("Bole")
                .phone("0911")
                .medicines("paracetamol")
                .openTime("8:00")
                .closeTime("20:00")
                .telegramId(77L)
                .status("PENDING")
                .build();
        when(registrationRepository.findById(15L)).thenReturn(Optional.of(registration));
        when(pharmacyRepository.save(any())).thenAnswer(inv -> {
            Pharmacy pharmacy = inv.getArgument(0);
            pharmacy.setId(4L);
            return pharmacy;
        });

        Long telegramId = service.approve(15L);

        assertEquals(77L, telegramId);
        assertEquals("APPROVED", registration.getStatus());
        verify(inventoryService).initializeInventoryFromMedicines(4L, "paracetamol");
        ArgumentCaptor<Pharmacy> captor = ArgumentCaptor.forClass(Pharmacy.class);
        verify(pharmacyRepository).save(captor.capture());
        assertTrue(captor.getValue().isApproved());
        assertEquals("City Rx", captor.getValue().getName());
    }

    @Test
    void saveLocation_noOpsWhenNoPendingRegistration() {
        when(registrationRepository.findFirstByTelegramIdAndStatusOrderByIdDesc(77L, "PENDING"))
                .thenReturn(Optional.empty());

        service.saveLocation(77L, 9.01, 38.75, "addr", "plus", "mall");

        verify(registrationRepository, never()).save(any());
    }
}
