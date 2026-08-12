package com.tenahub.bot.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiniAppActorResolverTest {

    @Mock
    private TelegramWebAppAuthService telegramWebAppAuthService;

    private MiniAppActorResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MiniAppActorResolver(telegramWebAppAuthService);
        ReflectionTestUtils.setField(resolver, "adminChatId", 55L);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requirePharmacyTelegramId_rejectsMismatch() {
        bindInitData("init");
        when(telegramWebAppAuthService.requireUserId("init")).thenReturn(100L);

        MiniAppAuthException error = assertThrows(MiniAppAuthException.class,
                () -> resolver.requirePharmacyTelegramId(200L, null));
        assertEquals("Telegram identity does not match pharmacyTelegramId", error.getMessage());
    }

    @Test
    void requirePharmacyTelegramId_returnsInitDataUser() {
        bindInitData("init");
        when(telegramWebAppAuthService.requireUserId("init")).thenReturn(100L);

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
