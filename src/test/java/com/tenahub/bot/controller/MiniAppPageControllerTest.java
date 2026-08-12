package com.tenahub.bot.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniAppPageControllerTest {

    private final MiniAppPageController controller = new MiniAppPageController();

    @Test
    void pharmacyPhotosPage_includesPharmacyId() {
        String html = controller.pharmacyPhotosPage(77L);

        assertTrue(html.contains("<!doctype html>"));
        assertTrue(html.contains("77"));
        assertTrue(html.contains("Pharmacy Photos"));
    }

    @Test
    void medicinePhotosPage_returnsHtml() {
        String html = controller.medicinePhotosPage();

        assertTrue(html.contains("<!doctype html>"));
        assertTrue(html.contains("Medicine Photos") || html.toLowerCase().contains("medicine"));
    }

    @Test
    void medicinePhotosPageById_includesMedicineId() {
        String html = controller.medicinePhotosPageById(19L);

        assertTrue(html.contains("19"));
    }
}
