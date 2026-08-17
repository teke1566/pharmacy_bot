package com.tenahub.bot.config;

import com.tenahub.bot.repository.PharmacyRegistrationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.RegistrationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
public class RegistrationServiceFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(RegistrationService.class)
    public RegistrationService registrationServiceFallback(
            PharmacyRegistrationRepository registrationRepository,
            PharmacyRepository pharmacyRepository,
            ObjectProvider<InventoryService> inventoryServiceProvider
    ) {
        InventoryService inventoryService = inventoryServiceProvider.getIfAvailable(NoOpInventoryService::new);
        try {
            Class<?> impl = Class.forName("com.tenahub.bot.service.impl.RegistrationServiceImpl");
            return (RegistrationService) impl
                    .getConstructor(PharmacyRegistrationRepository.class, PharmacyRepository.class, InventoryService.class)
                    .newInstance(registrationRepository, pharmacyRepository, inventoryService);
        } catch (Throwable ignored) {
            return new NoOpRegistrationService();
        }
    }

    private static class NoOpInventoryService implements InventoryService {
        @Override
        public void markOutOfStock(Long telegramId, String medicineName) {
        }

        @Override
        public List<PharmacyInventory> getInventory(Long telegramId) {
            return List.of();
        }

        @Override
        public List<PharmacyInventory> getInventoryByPharmacyId(Long pharmacyId) {
            return List.of();
        }

        @Override
        public byte[] exportInventoryCsv(Long telegramId) {
            return new byte[0];
        }

        @Override
        public String buildSummary(Long telegramId, String period) {
            return "Inventory service is not available.";
        }

        @Override
        public String buildLowStockAlert(Long telegramId) {
            return "Inventory service is not available.";
        }

        @Override
        public String buildExpiryAlert(Long telegramId) {
            return "Inventory service is not available.";
        }

        @Override
        public void setLowStockThreshold(Long telegramId, String medicineName, Integer threshold) {
        }

        @Override
        public String getDemandInsights(Long telegramId) {
            return "Inventory service is not available.";
        }

        @Override
        public String getAdvancedRestockSuggestions(Long telegramId) {
            return "Inventory service is not available.";
        }

        @Override
        public java.util.List<com.tenahub.bot.dto.RestockSuggestionDTO> listRestockSuggestions(Long telegramId) {
            return List.of();
        }

        @Override
        public void ignoreRestockSuggestion(Long telegramId, String medicineName) {
        }

        @Override
        public com.tenahub.bot.dto.InventoryCsvImportResultDTO importInventoryCsv(Long telegramId, String csvContent) {
            return com.tenahub.bot.dto.InventoryCsvImportResultDTO.builder()
                    .success(false)
                    .message("Inventory service is not available.")
                    .build();
        }

        @Override
        public void updatePrice(Long pharmacyChatId, String medicineName, BigDecimal price) {
        }

        @Override
        public void upsertStock(Long chatId, String medicineName, Integer quantity, BigDecimal price) {
        }

        @Override
        public PharmacyInventory setRequiresPrescription(Long telegramId, Long medicineId, boolean requiresPrescription) {
            return null;
        }

        @Override
        public PharmacyInventory setRequiresPrescriptionForPharmacy(Long pharmacyId, Long medicineId, boolean requiresPrescription) {
            return null;
        }

        @Override
        public BulkInventoryUpdateResult bulkUpsertFromText(Long chatId, String bulkText) {
            return new BulkInventoryUpdateResult(0, 0, 0, List.of("Inventory service is not available."));
        }

        @Override
        public void initializeInventoryFromMedicines(Long pharmacyId, String medicines) {
        }

        @Override
        public List<com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO> getPharmacyMiniAppInventory(Long pharmacyTelegramId) {
            return List.of();
        }

        @Override
        public List<com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO> getPharmacyMiniAppInventory(
                Long pharmacyTelegramId, String search, String stockStatus, String expiryStatus, Boolean includeArchived) {
            return List.of();
        }

        @Override
        public com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO updateStockFromMiniApp(Long pharmacyTelegramId, Long itemId, Integer quantity) {
            return null;
        }

        @Override
        public com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO updatePriceFromMiniApp(Long pharmacyTelegramId, Long itemId, BigDecimal price) {
            return null;
        }

        @Override
        public com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO togglePrescriptionFromMiniApp(Long pharmacyTelegramId, Long itemId, boolean requiresPrescription) {
            return null;
        }

        @Override
        public com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO toggleAvailabilityFromMiniApp(Long pharmacyTelegramId, Long itemId, boolean available) {
            return null;
        }

        @Override
        public com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO addStockFromMiniApp(Long pharmacyTelegramId, com.tenahub.bot.dto.PharmacyMiniAppAddStockRequest request) {
            return null;
        }

        @Override
        public com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO patchInventoryFromMiniApp(
                Long pharmacyTelegramId, Long itemId, com.tenahub.bot.dto.PharmacyMiniAppInventoryPatchRequest request) {
            return null;
        }

        @Override
        public void archiveInventoryItem(Long pharmacyTelegramId, Long itemId) {
        }

        @Override
        public java.util.List<com.tenahub.bot.dto.MedicineBatchDTO> listInventoryBatches(Long pharmacyTelegramId, Long itemId) {
            return List.of();
        }

        @Override
        public java.util.List<com.tenahub.bot.dto.StockMovementDTO> listInventoryMovements(Long pharmacyTelegramId, Long itemId) {
            return List.of();
        }

        @Override
        public java.util.List<com.tenahub.bot.dto.MedicineBatchDTO> listExpiryBatches(Long pharmacyTelegramId, String bucket) {
            return List.of();
        }

        @Override
        public com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO adjustInventoryFromMiniApp(
                Long pharmacyTelegramId,
                Long itemId,
                Integer quantityChange,
                String reason,
                String movementType,
                String batchNumber,
                LocalDate expiryDate) {
            return null;
        }
    }

    private static class NoOpRegistrationService implements RegistrationService {
        @Override
        public Long register(String name, String city, String area, String phone, String medicines, String openTime, String closeTime, Long telegramId) {
            return null;
        }

        @Override
        public void saveLocation(Long telegramId, Double latitude, Double longitude, String formattedAddress, String plusCode, String landmark) {
        }

        @Override
        public void saveLocationDetails(Long telegramId, String formattedAddress, String landmark, String plusCode) {
        }

        @Override
        public void saveLicenseExpiryDate(Long telegramId, LocalDate expiryDate) {
        }

        @Override
        public Long saveLicense(Long telegramId, String fileId) {
            return null;
        }

        @Override
        public Long approve(Long registrationId) {
            return null;
        }

        @Override
        public Long reject(Long registrationId) {
            return null;
        }

        @Override
        public void rejectWithReason(Long registrationId, String reason) {
        }

        @Override
        public boolean exists(Long telegramId) {
            return false;
        }

        @Override
        public boolean licenseAlreadyUploaded(Long telegramId) {
            return false;
        }

        @Override
        public boolean isProcessed(Long id) {
            return false;
        }

        @Override
        public PharmacyRegistration getRegistration(Long id) {
            return null;
        }

        @Override
        public Long getApprovedPharmacyId(Long telegramId) {
            return null;
        }

        @Override
        public boolean isRegisteredPharmacy(Long telegramId) {
            return false;
        }

        @Override
        public boolean existsById(Long id) {
            return false;
        }

        @Override
        public void deleteRegistration(Long id) {
        }

        @Override
        public void deletePendingByTelegramId(Long telegramId) {
        }

        @Override
        public void deleteRegistrationByTelegramId(Long telegramId) {
        }

        @Override
        public int deleteInvalidPendingRegistrations() {
            return 0;
        }

        @Override
        public PharmacyRegistration getLatestRejected(Long telegramId) {
            return null;
        }

        @Override
        public PharmacyRegistration getLatest(Long telegramId) {
            return null;
        }

        @Override
        public Long restartRejectedRegistration(Long telegramId) {
            return null;
        }
    }
}
