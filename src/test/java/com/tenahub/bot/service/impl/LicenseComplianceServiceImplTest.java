package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.LicenseComplianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseComplianceServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;

    private LicenseComplianceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LicenseComplianceServiceImpl(pharmacyRepository);
        ReflectionTestUtils.setField(service, "expiringSoonDays", 30);
    }

    @Test
    void listByCategory_filtersSuspendedPharmacies() {
        Pharmacy suspended = Pharmacy.builder()
                .id(1L)
                .name("Down")
                .licenseSuspended(true)
                .build();
        Pharmacy other = Pharmacy.builder()
                .id(2L)
                .name("Ok")
                .licenseSuspended(false)
                .licenseFileId("file")
                .licenseExpiryDate(LocalDate.now().plusDays(10))
                .build();
        when(pharmacyRepository.findByLicenseSuspendedTrueOrderByIdDesc()).thenReturn(List.of(suspended, other));

        List<LicenseComplianceService.ComplianceListItem> items =
                service.listByCategory(LicenseComplianceService.CATEGORY_SUSPENDED);

        assertEquals(1, items.size());
        assertEquals(1L, items.get(0).pharmacyId());
        assertEquals("Suspended", items.get(0).status());
    }

    @Test
    void suspendForCompliance_marksPharmacySuspended() {
        Pharmacy pharmacy = Pharmacy.builder().id(4L).name("City").approved(true).licenseSuspended(false).build();
        when(pharmacyRepository.findById(4L)).thenReturn(Optional.of(pharmacy));
        when(pharmacyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LicenseComplianceService.ComplianceDetail detail = service.suspendForCompliance(4L, 55L);

        ArgumentCaptor<Pharmacy> captor = ArgumentCaptor.forClass(Pharmacy.class);
        verify(pharmacyRepository).save(captor.capture());
        assertFalse(captor.getValue().isApproved());
        assertTrue(captor.getValue().isLicenseSuspended());
        assertEquals("SUSPEND_COMPLIANCE", captor.getValue().getLastComplianceAction());
        assertEquals(4L, detail.pharmacyId());
    }

    @Test
    void extendGracePeriod_rejectsNonPositiveDays() {
        assertThrows(RuntimeException.class, () -> service.extendGracePeriod(1L, 0, 55L));
    }

    @Test
    void getDetail_throwsWhenMissing() {
        when(pharmacyRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getDetail(9L));
    }
}
