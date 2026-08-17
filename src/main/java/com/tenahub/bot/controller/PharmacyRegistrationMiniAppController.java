package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.PharmacyRegistrationMiniAppService;
import com.tenahub.bot.service.TelegramWebAppAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/pharmacy/registration", "/proxyapi/api/pharmacy/registration"})
public class PharmacyRegistrationMiniAppController {

    private final PharmacyRegistrationMiniAppService registrationMiniAppService;
    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final MiniAppActorResolver miniAppActorResolver;

    public PharmacyRegistrationMiniAppController(
            PharmacyRegistrationMiniAppService registrationMiniAppService,
            TelegramWebAppAuthService telegramWebAppAuthService,
            MiniAppActorResolver miniAppActorResolver) {
        this.registrationMiniAppService = registrationMiniAppService;
        this.telegramWebAppAuthService = telegramWebAppAuthService;
        this.miniAppActorResolver = miniAppActorResolver;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        try {
            return ResponseEntity.ok(registrationMiniAppService.getStatus(requireUserId()));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return conflict(e.getMessage());
        }
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submit(
            @RequestParam("name") String name,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "area", required = false) String area,
            @RequestParam("phone") String phone,
            @RequestParam("medicines") String medicines,
            @RequestParam("openTime") String openTime,
            @RequestParam("closeTime") String closeTime,
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam(value = "formattedAddress", required = false) String formattedAddress,
            @RequestParam(value = "landmark", required = false) String landmark,
            @RequestParam(value = "plusCode", required = false) String plusCode,
            @RequestParam("licenseExpiryDate") String licenseExpiryDate,
            @RequestParam("license") MultipartFile license) {
        try {
            return ResponseEntity.ok(registrationMiniAppService.submit(
                    requireUserId(),
                    name,
                    city,
                    area,
                    phone,
                    medicines,
                    openTime,
                    closeTime,
                    latitude,
                    longitude,
                    formattedAddress,
                    landmark,
                    plusCode,
                    licenseExpiryDate,
                    license));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return conflict(e.getMessage());
        }
    }

    @PostMapping("/restart")
    public ResponseEntity<?> restart() {
        try {
            return ResponseEntity.ok(registrationMiniAppService.restart(requireUserId()));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return conflict(e.getMessage());
        }
    }

    private Long requireUserId() {
        return telegramWebAppAuthService.requireUserId(miniAppActorResolver.currentInitData());
    }

    private static ResponseEntity<MiniAppOperationResponseDTO> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(message).build());
    }

    private static ResponseEntity<MiniAppOperationResponseDTO> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(message).build());
    }
}
