package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.MiniAppAuthException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:15MB}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:30MB}")
    private String maxRequestSize;

    @ExceptionHandler(MiniAppAuthException.class)
    public ResponseEntity<MiniAppOperationResponseDTO> handleMiniAppAuth(MiniAppAuthException exception) {
        log.warn("Mini App auth rejected: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(MiniAppOperationResponseDTO.builder()
                        .success(false)
                        .message(exception.getMessage())
                        .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception,
                                                              HttpServletRequest request) {
        long requestSizeBytes = request.getContentLengthLong();
        long rejectedLimitBytes = exception.getMaxUploadSize();

        log.warn("Prescription upload rejected: path={}, requestSize={}, rejectedLimit={}, configuredMaxFileSize={}, configuredMaxRequestSize={}",
                request.getRequestURI(),
                formatBytes(requestSizeBytes),
                formatBytes(rejectedLimitBytes),
                maxFileSize,
                maxRequestSize);

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("Prescription image upload is too large. Maximum allowed size is "
                        + maxFileSize + " per file and " + maxRequestSize
                        + " per upload. Please compress the image or upload fewer files and try again.");
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        double megabytes = bytes / (1024.0 * 1024.0);
        return String.format("%.2f MB", megabytes);
    }
}