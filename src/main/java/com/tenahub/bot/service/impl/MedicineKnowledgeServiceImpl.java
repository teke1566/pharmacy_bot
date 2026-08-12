package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MedicineInfoDTO;
import com.tenahub.bot.service.MedicineKnowledgeService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Curated medicine knowledge base for common drugs.
 * Responses are general educational content only — not personalized medical advice.
 * All entries include a standard safety note directing users to consult a pharmacist
 * or clinician for personal dosing, interactions, pregnancy, allergy, or child dosing.
 */
@Service
public class MedicineKnowledgeServiceImpl implements MedicineKnowledgeService {

    private static final String SAFETY_NOTE =
            "Consult a pharmacist or clinician for personal dosing, pregnancy, child dosing, allergy concerns, or drug interactions.";

    private final Map<String, MedicineInfoDTO> registry;
    private final Map<String, String> aliases;

    public MedicineKnowledgeServiceImpl() {
        registry = buildRegistry();
        aliases = buildAliases();
    }

    @Override
    public Optional<MedicineInfoDTO> lookup(String medicineName) {
        if (medicineName == null) return Optional.empty();
        String key = medicineName.toLowerCase(Locale.ROOT).trim();
        MedicineInfoDTO found = registry.get(key);
        if (found != null) return Optional.of(found);
        String canonical = aliases.get(key);
        if (canonical != null) {
            found = registry.get(canonical);
            if (found != null) return Optional.of(found);
        }
        return Optional.empty();
    }

    @Override
    public String detectMedicineName(String message) {
        if (message == null) return null;
        String text = message.toLowerCase(Locale.ROOT);
        for (String name : registry.keySet()) {
            if (text.contains(name)) return name;
        }
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (text.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    // ── Registry ────────────────────────────────────────────────────────────
    private Map<String, MedicineInfoDTO> buildRegistry() {
        Map<String, MedicineInfoDTO> map = new LinkedHashMap<>();

        map.put("paracetamol", MedicineInfoDTO.builder()
                .name("Paracetamol")
                .use("Used to relieve mild to moderate pain such as headache, toothache, and muscle aches, and to reduce fever.")
                .howToTake("Take 500–1000 mg by mouth every 4–6 hours as needed. Do not exceed 4000 mg per day. Can be taken with or without food.")
                .sideEffects("Rarely causes side effects at recommended doses. Overdose — even a moderate one — can cause serious and irreversible liver damage.")
                .warnings("Avoid alcohol while taking paracetamol. Caution with liver disease. Do not combine with other products containing paracetamol.")
                .storage("Store at room temperature (15–30°C), away from heat and moisture.")
                .missedDose("Take as soon as you remember. If it is close to the time for your next dose, skip the missed dose. Do not double up.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("amoxicillin", MedicineInfoDTO.builder()
                .name("Amoxicillin")
                .use("Antibiotic used to treat bacterial infections including ear, throat, respiratory tract, urinary tract, and skin infections.")
                .howToTake("Take every 8 or 12 hours — with or without food — as prescribed. Always complete the full prescribed course even if you feel better.")
                .sideEffects("Diarrhea, nausea, and rash are common. Rare but serious: severe allergic reaction (anaphylaxis) — seek emergency help immediately if you develop difficulty breathing or severe swelling.")
                .warnings("Do not use if you have a penicillin allergy. Never stop early — incomplete antibiotic courses promote drug resistance.")
                .storage("Capsules/tablets: room temperature. Liquid suspension: refrigerate and use within 14 days.")
                .missedDose("Take as soon as remembered. If close to the next dose time, skip. Do not double the dose.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("ibuprofen", MedicineInfoDTO.builder()
                .name("Ibuprofen")
                .use("NSAID used to reduce fever, relieve pain (headache, dental pain, menstrual cramps, muscle pain), and treat mild inflammation.")
                .howToTake("Take with food or milk to protect the stomach. Usual adult OTC dose: 200–400 mg every 4–6 hours. Maximum 1200 mg/day without medical supervision.")
                .sideEffects("Stomach upset, heartburn, and nausea. Long-term use: increased risk of peptic ulcers, kidney stress, fluid retention, and raised blood pressure.")
                .warnings("Never take on an empty stomach. Caution with kidney/liver disease, heart conditions, or peptic ulcer history. Avoid in the third trimester of pregnancy.")
                .storage("Store at room temperature (20–25°C), away from heat and moisture.")
                .missedDose("Take as soon as remembered with food. If near the next dose time, skip. Do not double.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("metformin", MedicineInfoDTO.builder()
                .name("Metformin")
                .use("First-line oral medication for type 2 diabetes. Lowers blood sugar primarily by reducing glucose production by the liver.")
                .howToTake("Take with meals to minimize stomach side effects. Standard-release: two to three times daily with food. Extended-release: once daily with the evening meal.")
                .sideEffects("Nausea, diarrhea, and stomach upset are common at the start — these usually improve within a few weeks. Rare but serious: lactic acidosis.")
                .warnings("Stop temporarily before procedures requiring iodine contrast dye (e.g., CT scans) and restart only on medical advice. Caution with significant kidney disease. Limit heavy alcohol use.")
                .storage("Store at room temperature, away from moisture and heat.")
                .missedDose("Take with your next meal if remembered soon after. Skip if close to the next scheduled dose. Do not double.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("insulin", MedicineInfoDTO.builder()
                .name("Insulin")
                .use("Used to control blood sugar in type 1 diabetes and in type 2 diabetes when oral medications no longer provide sufficient control.")
                .howToTake("Injected under the skin (subcutaneously). Timing and type depend entirely on your prescription — follow your diabetes care plan exactly. Never switch brands or types without medical guidance.")
                .sideEffects("Hypoglycemia (low blood sugar) is the most common risk: symptoms include shakiness, sweating, rapid heartbeat, and confusion. Also: injection site reactions such as redness or bruising.")
                .warnings("Never skip doses without medical advice. Know how to recognize and treat hypoglycemia. Inspect insulin before use — do not use if it looks cloudy (unless it is supposed to be), discolored, or has particles.")
                .storage("Unopened vials/pens: refrigerate at 2–8°C. Open vial or pen: can be kept at room temperature for up to 28–30 days depending on insulin type. Never freeze.")
                .missedDose("For rapid-acting insulin: only take if about to eat. For long-acting insulin: consult your care team. Do not double up under any circumstances.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("omeprazole", MedicineInfoDTO.builder()
                .name("Omeprazole")
                .use("Proton pump inhibitor (PPI) used to treat stomach and duodenal ulcers, GERD (acid reflux/chronic heartburn), and as part of H. pylori eradication therapy.")
                .howToTake("Take 30–60 minutes before a meal, usually once daily in the morning. Swallow the capsule whole — do not crush or chew.")
                .sideEffects("Headache, nausea, diarrhea, stomach pain, and constipation. Long-term use carries a small risk of low magnesium and vitamin B12 deficiency.")
                .warnings("Not suitable for immediate relief of acute heartburn — it takes several days to reach full effect. Long-term use should be periodically reassessed. May mask symptoms of stomach cancer.")
                .storage("Store at room temperature (15–30°C), protected from moisture.")
                .missedDose("Take as soon as remembered before a meal. Skip if it is close to your next scheduled dose. Do not double.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("ciprofloxacin", MedicineInfoDTO.builder()
                .name("Ciprofloxacin")
                .use("Fluoroquinolone antibiotic for urinary tract infections, respiratory, bone/joint, skin, abdominal, and typhoid infections.")
                .howToTake("Take on an empty stomach or with a light meal. Drink plenty of water to reduce crystalluria risk. Complete the full prescribed course.")
                .sideEffects("Nausea, diarrhea, headache, dizziness. Rare but serious: tendon damage (especially Achilles), peripheral nerve damage, and photosensitivity.")
                .warnings("Avoid antacids, dairy products, and calcium-fortified foods within 2 hours of dosing. Limit sun exposure and use sunscreen. Not first-line in children or pregnant women due to joint/cartilage concerns.")
                .storage("Store at room temperature (15–30°C), away from light and moisture.")
                .missedDose("Take as soon as remembered. If close to next dose, skip. Do not double. Complete the course.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("diclofenac", MedicineInfoDTO.builder()
                .name("Diclofenac")
                .use("NSAID used to relieve pain, reduce inflammation, and treat arthritis, muscle pain, and menstrual cramps.")
                .howToTake("Take oral forms with food to reduce stomach irritation. Usual dose: 50 mg two to three times daily as prescribed. Topical gel: apply a thin layer to the affected area as directed.")
                .sideEffects("GI upset, heartburn, and headache. Chronic use raises the risk of peptic ulcers, kidney and liver stress, and cardiovascular events.")
                .warnings("Avoid in active peptic ulcer, severe kidney/liver/heart disease. Use with caution in the elderly. Avoid during the last trimester of pregnancy.")
                .storage("Store at room temperature, protected from light and moisture.")
                .missedDose("Take with food as soon as remembered. Skip if close to your next dose. Do not double.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("chloroquine", MedicineInfoDTO.builder()
                .name("Chloroquine")
                .use("Used to prevent and treat malaria. Also used in lower doses for rheumatoid arthritis and systemic lupus erythematosus.")
                .howToTake("Take with food or milk to reduce stomach upset. Malaria prophylaxis: once weekly on the same day. For treatment: follow your prescription timing exactly.")
                .sideEffects("Nausea, stomach cramps, headache, and dizziness. Long-term use: rare but serious progressive eye damage (retinopathy).")
                .warnings("Regular eye exams are recommended with long-term use. Caution with existing heart arrhythmias (QT prolongation risk). Resistance is common in some malaria-endemic regions — confirm suitability with a clinician.")
                .storage("Store at room temperature, protected from light.")
                .missedDose("Take as soon as remembered. Skip if close to the next dose. For weekly prophylaxis, resume on your regular day. Do not double.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("cotrimoxazole", MedicineInfoDTO.builder()
                .name("Cotrimoxazole")
                .use("Antibiotic combination (trimethoprim + sulfamethoxazole) for urinary tract infections, respiratory infections, traveler's diarrhea, and prophylaxis against Pneumocystis pneumonia.")
                .howToTake("Take with a full glass of water, with or without food. Complete the full prescribed course.")
                .sideEffects("Nausea, vomiting, and rash. Rare but serious: severe blistering skin reactions (Stevens-Johnson syndrome) and blood count abnormalities.")
                .warnings("Stop immediately and seek medical help if a severe or blistering rash develops. Caution with kidney impairment or G6PD deficiency. Avoid during the last weeks of pregnancy.")
                .storage("Store at room temperature, protected from light and moisture.")
                .missedDose("Take as soon as remembered. Skip if close to the next dose. Complete the full course.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("atenolol", MedicineInfoDTO.builder()
                .name("Atenolol")
                .use("Beta-blocker used to treat high blood pressure, angina (chest pain), and certain heart rhythm disorders.")
                .howToTake("Take once daily, with or without food. Do not stop abruptly — the dose must be tapered gradually under medical supervision.")
                .sideEffects("Fatigue, cold hands and feet, slow pulse, and dizziness. May mask symptoms of low blood sugar (hypoglycemia) in people with diabetes.")
                .warnings("Never stop abruptly — sudden discontinuation can trigger angina or heart attack. Use with caution in asthma, COPD, and diabetes.")
                .storage("Store at room temperature, away from moisture.")
                .missedDose("Take as soon as remembered. If near the next dose time, skip. Do not double. Do not stop abruptly without medical advice.")
                .safetyNote(SAFETY_NOTE).build());

        map.put("lisinopril", MedicineInfoDTO.builder()
                .name("Lisinopril")
                .use("ACE inhibitor used to treat high blood pressure, heart failure, and to protect kidney function in people with diabetes.")
                .howToTake("Take once daily, with or without food, at the same time each day.")
                .sideEffects("A persistent dry cough is common. Dizziness and elevated potassium may occur. Rare but serious: angioedema (sudden swelling of the face, lips, tongue, or throat — seek emergency help immediately).")
                .warnings("Do not use during pregnancy — can cause serious fetal harm. Avoid regular NSAID use with this medication. Do not take potassium supplements or salt substitutes without medical guidance.")
                .storage("Store at room temperature, away from moisture.")
                .missedDose("Take as soon as remembered. Skip if close to your next dose. Do not double.")
                .safetyNote(SAFETY_NOTE).build());

        return Collections.unmodifiableMap(map);
    }

    // ── Aliases (brand names and common alternate spellings) ─────────────────
    private Map<String, String> buildAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        // paracetamol
        map.put("acetaminophen", "paracetamol");
        map.put("tylenol", "paracetamol");
        map.put("panadol", "paracetamol");
        // ibuprofen
        map.put("advil", "ibuprofen");
        map.put("nurofen", "ibuprofen");
        map.put("brufen", "ibuprofen");
        map.put("motrin", "ibuprofen");
        // metformin
        map.put("glucophage", "metformin");
        // omeprazole
        map.put("prilosec", "omeprazole");
        map.put("losec", "omeprazole");
        // ciprofloxacin
        map.put("cipro", "ciprofloxacin");
        map.put("ciproxin", "ciprofloxacin");
        // cotrimoxazole
        map.put("bactrim", "cotrimoxazole");
        map.put("septrin", "cotrimoxazole");
        map.put("trimethoprim", "cotrimoxazole");
        // chloroquine
        map.put("hydroxychloroquine", "chloroquine");
        map.put("plaquenil", "chloroquine");
        // diclofenac
        map.put("voltaren", "diclofenac");
        map.put("voltarol", "diclofenac");
        map.put("cataflam", "diclofenac");
        return Collections.unmodifiableMap(map);
    }
}
