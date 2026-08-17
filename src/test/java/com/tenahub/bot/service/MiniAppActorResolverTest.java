package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyStaff;
import com.tenahub.bot.entity.PharmacyStaffRole;
import com.tenahub.bot.entity.PharmacyStaffStatus;
import com.tenahub.bot.repository.PharmacyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiniAppActorResolverTest {

    @Mock
    private TelegramWebAppAuthService telegramWebAppAuthService;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyStaffAccessService pharmacyStaffAccessService;

    private MiniAppActorResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MiniAppActorResolver(telegramWebAppAuthService, pharmacyRepository, pharmacyStaffAccessService);
        ReflectionTestUtils.setField(resolver, "adminChatId", 55L);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requirePharmacyTelegramId_rejectsUnknownStaffForClaimedPharmacy() {
        bindInitData("init");
        when(telegramWebAppAuthService.requireUserId("init")).thenReturn(100L);
        Pharmacy pharmacy = Pharmacy.builder().id(1L).telegramId(200L).build();
        when(pharmacyRepository.findByTelegramId(200L)).thenReturn(Optional.of(pharmacy));
        when(pharmacyStaffAccessService.resolveActor(pharmacy, 100L))
                .thenThrow(new MiniAppAuthException("Staff does not belong to this pharmacy"));

        MiniAppAuthException error = assertThrows(MiniAppAuthException.class,
                () -> resolver.requirePharmacyTelegramId(200L, null));
        assertEquals("Staff does not belong to this pharmacy", error.getMessage());
    }

    @Test
    void requirePharmacyTelegramId_allowsOwner() {
        bindInitData("init");
        when(telegramWebAppAuthService.requireUserId("init")).thenReturn(100L);
        Pharmacy pharmacy = Pharmacy.builder().id(1L).telegramId(100L).build();
        when(pharmacyRepository.findByTelegramId(100L)).thenReturn(Optional.of(pharmacy));
        when(pharmacyStaffAccessService.resolveActor(pharmacy, 100L)).thenReturn(PharmacyActor.builder()
                .pharmacyId(1L)
                .pharmacyTelegramId(100L)
                .actorTelegramId(100L)
                .role(PharmacyStaffRole.PHARMACY_OWNER)
                .permissions(Set.of())
                .build());

        assertEquals(100L, resolver.requirePharmacyTelegramId(100L, null));
    }

    @Test
    void requirePharmacyTelegramId_allowsActiveStaffWithOwnerClaim() {
        bindInitData("init");
        when(telegramWebAppAuthService.requireUserId("init")).thenReturn(777L);
        Pharmacy pharmacy = Pharmacy.builder().id(1L).telegramId(100L).build();
        when(pharmacyRepository.findByTelegramId(100L)).thenReturn(Optional.of(pharmacy));
        when(pharmacyStaffAccessService.resolveActor(pharmacy, 777L)).thenReturn(PharmacyActor.builder()
                .pharmacyId(1L)
                .pharmacyTelegramId(100L)
                .actorTelegramId(777L)
                .staffId(9L)
                .role(PharmacyStaffRole.PHARMACIST)
                .permissions(Set.of())
                .build());

        assertEquals(100L, resolver.requirePharmacyTelegramId(100L, null));
    }

    @Test
    void requireAdminTelegramId_rejectsNonAdmin() {
        bindInitData("init");
        when(telegramWebAppAuthService.requireUserId("init")).thenReturn(100L);

        MiniAppAuthException error = assertThrows(MiniAppAuthException.class,
                () -> resolver.requireAdminTelegramId(100L, null));
        assertEquals("Access denied — admin only", error.getMessage());
    }

    @Test
    void requireAdminTelegramId_allowsConfiguredAdmin() {
        bindInitData("init");
        when(telegramWebAppAuthService.requireUserId("init")).thenReturn(55L);

        assertEquals(55L, resolver.requireAdminTelegramId(55L, null));
    }

    @Test
    void resolveAdminIdForAccessCheck_requiresInitData() {
        assertThrows(MiniAppAuthException.class,
                () -> resolver.resolveAdminIdForAccessCheck(77L, null));
    }

    @Test
    void resolveAdminIdForAccessCheck_usesInitDataAndRejectsMismatch() {
        bindInitData("init");
        when(telegramWebAppAuthService.requireUserId("init")).thenReturn(55L);

        assertEquals(55L, resolver.resolveAdminIdForAccessCheck(55L, 88L));
        assertThrows(MiniAppAuthException.class,
                () -> resolver.resolveAdminIdForAccessCheck(77L, 88L));
    }

    private void bindInitData(String initData) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MiniAppActorResolver.INIT_DATA_HEADER, initData);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
