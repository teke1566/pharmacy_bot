package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.UserLocation;
import com.tenahub.bot.repository.UserLocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLocationServiceImplTest {

    @Mock
    private UserLocationRepository repository;

    @InjectMocks
    private UserLocationServiceImpl service;

    @Test
    void saveLocation_createsDefaultDisplayName() {
        when(repository.findByTelegramId(7L)).thenReturn(Optional.empty());

        service.saveLocation(7L, 9.01, 38.75);

        ArgumentCaptor<UserLocation> captor = ArgumentCaptor.forClass(UserLocation.class);
        verify(repository).save(captor.capture());
        assertEquals(7L, captor.getValue().getTelegramId());
        assertEquals(9.01, captor.getValue().getLatitude());
        assertEquals(38.75, captor.getValue().getLongitude());
        assertEquals("Exact location saved", captor.getValue().getDisplayName());
    }

    @Test
    void saveLocation_keepsExistingDisplayName() {
        UserLocation existing = UserLocation.builder()
                .telegramId(7L)
                .displayName("Bole")
                .build();
        when(repository.findByTelegramId(7L)).thenReturn(Optional.of(existing));

        service.saveLocation(7L, 9.02, 38.76);

        ArgumentCaptor<UserLocation> captor = ArgumentCaptor.forClass(UserLocation.class);
        verify(repository).save(captor.capture());
        assertEquals("Bole", captor.getValue().getDisplayName());
        assertEquals(9.02, captor.getValue().getLatitude());
    }

    @Test
    void saveLocation_withDetailsSetsAddressFields() {
        when(repository.findByTelegramId(7L)).thenReturn(Optional.empty());

        service.saveLocation(7L, 9.01, 38.75, "Addis", "Addis Ababa", "Bole", "Atlas", "Near Atlas");

        ArgumentCaptor<UserLocation> captor = ArgumentCaptor.forClass(UserLocation.class);
        verify(repository).save(captor.capture());
        assertEquals("Addis Ababa", captor.getValue().getCity());
        assertEquals("Near Atlas", captor.getValue().getDisplayName());
    }

    @Test
    void getLocation_returnsNullWhenMissing() {
        when(repository.findByTelegramId(1L)).thenReturn(Optional.empty());

        assertNull(service.getLocation(1L));
    }
}
