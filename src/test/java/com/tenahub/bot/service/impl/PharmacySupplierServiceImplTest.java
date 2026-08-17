package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineBatch;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacySupplier;
import com.tenahub.bot.repository.MedicineBatchRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacySupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacySupplierServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacySupplierRepository supplierRepository;
    @Mock
    private MedicineBatchRepository batchRepository;

    private PharmacySupplierServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PharmacySupplierServiceImpl(pharmacyRepository, supplierRepository, batchRepository);
    }

    @Test
    void get_otherPharmacy_throws() {
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(3L).telegramId(9001L).build()));
        when(supplierRepository.findByIdAndPharmacyId(8L, 3L)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.get(9001L, 8L));
        assertTrue(error.getMessage().contains("does not belong"));
    }

    @Test
    void get_includesSuppliedMedicines() {
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(3L).telegramId(9001L).build()));
        when(supplierRepository.findByIdAndPharmacyId(8L, 3L)).thenReturn(Optional.of(
                PharmacySupplier.builder().id(8L).pharmacyId(3L).name("MediSupply").status("ACTIVE").build()));
        when(batchRepository.findByPharmacyId(3L)).thenReturn(List.of(
                MedicineBatch.builder().supplierId(8L).medicineName("insulin").build(),
                MedicineBatch.builder().supplier("MediSupply").medicineName("paracetamol").build()));

        var dto = service.get(9001L, 8L);

        assertEquals("MediSupply", dto.getName());
        assertEquals(2, dto.getSuppliedMedicines().size());
        assertTrue(dto.getSuppliedMedicines().contains("insulin"));
        assertTrue(dto.getSuppliedMedicines().contains("paracetamol"));
    }
}
