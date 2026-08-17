package com.tenahub.bot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicineSearchNormalizerTest {

    @Test
    void analogueSearchStem_sharesInsulinFamily() {
        assertEquals("insulin", MedicineSearchNormalizer.analogueSearchStem("insulin"));
        assertEquals("insulin", MedicineSearchNormalizer.analogueSearchStem("insulin glargine"));
        assertEquals("insulin", MedicineSearchNormalizer.analogueSearchStem("Insulin Lispro"));
        assertTrue(MedicineSearchNormalizer.sharesAnalogueStem("insulin", "insulin glargine"));
        assertTrue(MedicineSearchNormalizer.sharesAnalogueStem("ኢንሱሊን ግላርጂን", "insulin lispro"));
    }

    @Test
    void catalogActiveIngredient_usesInsulinTokenForRegularAndNph() {
        assertEquals("insulin", MedicineSearchNormalizer.catalogActiveIngredient("regular insulin"));
        assertEquals("insulin", MedicineSearchNormalizer.catalogActiveIngredient("nph insulin"));
        assertEquals("insulin", MedicineSearchNormalizer.catalogActiveIngredient("insulin glargine"));
        assertEquals("paracetamol", MedicineSearchNormalizer.catalogActiveIngredient("paracetamol 500mg"));
    }

    @Test
    void catalogStrengthAndDosageForm_parseFromStoredName() {
        assertEquals("500mg", MedicineSearchNormalizer.catalogStrength("paracetamol 500mg tab"));
        assertEquals("tablet", MedicineSearchNormalizer.catalogDosageForm("paracetamol 500mg tab"));
        assertEquals("100iu/ml", MedicineSearchNormalizer.catalogStrength("insulin 100 IU/ml injection"));
        assertEquals("injection", MedicineSearchNormalizer.catalogDosageForm("insulin 100 IU/ml injection"));
        assertEquals("", MedicineSearchNormalizer.catalogStrength("insulin"));
        assertEquals("", MedicineSearchNormalizer.catalogDosageForm("insulin"));
    }
}
