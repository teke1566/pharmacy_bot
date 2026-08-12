package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.AdminInboxItem;
import com.tenahub.bot.entity.AdminInboxItemStatus;
import com.tenahub.bot.entity.AdminInboxItemType;
import com.tenahub.bot.repository.AdminInboxItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInboxServiceImplTest {

    @Mock
    private AdminInboxItemRepository adminInboxItemRepository;

    @InjectMocks
    private AdminInboxServiceImpl service;

    @Test
    void createFeedbackItem_savesNewFeedback() {
        service.createFeedbackItem(42L, "  thanks  ");

        ArgumentCaptor<AdminInboxItem> captor = ArgumentCaptor.forClass(AdminInboxItem.class);
        verify(adminInboxItemRepository).save(captor.capture());
        assertEquals(AdminInboxItemType.FEEDBACK, captor.getValue().getType());
        assertEquals(AdminInboxItemStatus.NEW, captor.getValue().getStatus());
        assertEquals("thanks", captor.getValue().getMessageText());
        assertEquals(42L, captor.getValue().getUserTelegramId());
    }

    @Test
    void createFeedbackItem_usesNaWhenBlank() {
        service.createFeedbackItem(1L, "   ");

        ArgumentCaptor<AdminInboxItem> captor = ArgumentCaptor.forClass(AdminInboxItem.class);
        verify(adminInboxItemRepository).save(captor.capture());
        assertEquals("N/A", captor.getValue().getMessageText());
    }

    @Test
    void markInReview_updatesStatus() {
        AdminInboxItem item = AdminInboxItem.builder()
                .id(8L)
                .status(AdminInboxItemStatus.NEW)
                .build();
        when(adminInboxItemRepository.findById(8L)).thenReturn(Optional.of(item));
        when(adminInboxItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminInboxItem updated = service.markInReview(8L);

        assertEquals(AdminInboxItemStatus.IN_REVIEW, updated.getStatus());
    }

    @Test
    void getById_throwsWhenMissing() {
        when(adminInboxItemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getById(1L));
    }
}
