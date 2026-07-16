package com.meddelivery.config;

import com.meddelivery.model.*;
import com.meddelivery.model.enums.BranchStatus;
import com.meddelivery.model.enums.LocationInputType;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Seeds demo USER ACCOUNTS (and the pharmacies/branches they belong to) plus
 * per-branch INVENTORY so the app can be tested/demoed without going through the
 * email-based invitation and OTP flows. Every account is created active/verified
 * with a shared known password — log in and use the system as normal.
 *
 * Opt-in only: set app.seed.demo.enabled=true (env APP_SEED_DEMO_ENABLED=true).
 *
 * Two independently-guarded phases (so inventory can be back-filled even when the
 * accounts already exist from an earlier run):
 *   1. seedAccounts()          — skipped if the KIPHARMA pharmacy already exists.
 *   2. seedInventoryIfMissing()— stocks any demo branch that currently has no inventory.
 * Both are idempotent, so it is safe to leave enabled across restarts.
 */
@Slf4j
@Component
@Order(20) // run after DataSeeder (super admin)
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final BranchRepository branchRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final PharmacistRepository pharmacistRepository;
    private final BranchManagerProfileRepository branchManagerProfileRepository;
    private final MedicineRepository medicineRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final StockEntryRepository stockEntryRepository;
    private final InsuranceProviderRepository insuranceProviderRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.demo.enabled:false}")
    private boolean demoEnabled;

    /** Shared password for every seeded demo account (documented for the defense). */
    private static final String DEMO_PASSWORD = "Demo@1234";
    private static final String MARKER_CODE = "KIPHARMA"; // pharmacy code used to detect prior seeding
    private static final Set<String> DEMO_CODES =
            Set.of("KIPHARMA", "BELVEDERE", "VINE", "CONSEIL", "MILLECOLLINES", "KASHA");

    @Override
    @Transactional
    public void run(String... args) {
        if (!demoEnabled) {
            log.info("Demo seeding disabled (app.seed.demo.enabled=false) — skipping DemoDataSeeder");
            return;
        }
        try {
            boolean accountsExist = pharmacyRepository.findAll().stream()
                    .anyMatch(p -> MARKER_CODE.equals(p.getPharmacyCode()));
            if (accountsExist) {
                log.info("Demo accounts already exist — skipping account seeding");
            } else {
                seedAccounts();
            }
            seedInventoryIfMissing();
            seedInsuranceProvidersIfMissing();
        } catch (Exception e) {
            log.error("Demo seeding failed: {}", e.getMessage(), e);
        }
    }

    // ── Phase 3: real Rwandan health-insurance providers (idempotent per code) ──

    /** name, code, coveragePercentage for each real Rwandan health insurer. */
    private static final Object[][] INSURERS = {
        {"Mutuelle de Santé (RSSB – CBHI)", "CBHI", 90.0},
        {"RSSB Medical Scheme (ex-RAMA)", "RSSB", 85.0},
        {"Military Medical Insurance (MMI)", "MMI", 85.0},
        {"Radiant Insurance", "RADIANT", 80.0},
        {"Prime Insurance", "PRIME", 80.0},
        {"Britam Insurance Rwanda", "BRITAM", 80.0},
        {"Sanlam Rwanda", "SANLAM", 80.0},
        {"UAP Insurance Rwanda", "UAP", 80.0},
        {"Eden Care Medical", "EDENCARE", 80.0},
        {"Old Mutual Rwanda", "OLDMUTUAL", 80.0},
    };

    private void seedInsuranceProvidersIfMissing() {
        int added = 0;
        for (Object[] p : INSURERS) {
            String name = (String) p[0];
            String code = (String) p[1];
            double coverage = (Double) p[2];
            if (insuranceProviderRepository.existsByCodeIgnoreCase(code)) {
                continue; // already present — leave it (and any manual edits) untouched
            }
            insuranceProviderRepository.save(InsuranceProvider.builder()
                    .name(name)
                    .code(code)
                    .coveragePercentage(coverage)
                    .build());
            added++;
        }
        if (added == 0) {
            log.info("Insurance providers already present — skipping insurance seeding");
        } else {
            log.info("Seeded {} insurance provider(s)", added);
        }
    }

    // ── Phase 1: accounts (pharmacies, branches, staff, patients) ───────────────

    private void seedAccounts() {
        List<String[]> creds = new ArrayList<>();

        // ── Kipharma — one pharmacy, TWO branches (City Centre + Kisimenti) ─
        Pharmacy kipharma = createPharmacy("Kipharma", "KIPHARMA", "Nyarugenge, KN 4 Ave", -1.9499, 30.0588, creds);
        createBranch(kipharma, "City Centre", "KIPHARMA-CTR", "Nyarugenge, KN 4 Ave", -1.9499, 30.0588, creds);
        createBranch(kipharma, "Kisimenti",   "KIPHARMA-KIS", "Kisimenti, Remera, KG 11 Ave", -1.9630, 30.1160, creds);

        // ── Independent single-branch pharmacies ────────────────────────────
        Pharmacy belvedere = createPharmacy("Pharmacie Belvédère", "BELVEDERE", "Nyamirambo, KN 2 St", -1.9806, 30.0386, creds);
        createBranch(belvedere, "Nyamirambo", "BELVEDERE-NYA", "Nyamirambo, KN 2 St", -1.9806, 30.0386, creds);

        Pharmacy vine = createPharmacy("Vine Pharmacy", "VINE", "Remera, KG 11 Ave", -1.9560, 30.1090, creds);
        createBranch(vine, "Kisimenti (Remera)", "VINE-REM", "Remera, KG 11 Ave", -1.9560, 30.1090, creds);

        Pharmacy conseil = createPharmacy("Pharmacie Conseil", "CONSEIL", "Kacyiru, KG 7 Ave", -1.9300, 30.0890, creds);
        createBranch(conseil, "Kacyiru", "CONSEIL-KAC", "Kacyiru, KG 7 Ave", -1.9300, 30.0890, creds);

        Pharmacy mille = createPharmacy("Pharmacie de la Mille Collines", "MILLECOLLINES", "Kimironko, KG 17 Ave", -1.9355, 30.1186, creds);
        createBranch(mille, "Kimironko", "MILLECOLLINES-KIM", "Kimironko, KG 17 Ave", -1.9355, 30.1186, creds);

        // ── Kasha Pharmacy — Gikondo industrial zone ────────────────────────
        Pharmacy kasha = createPharmacy("Kasha Pharmacy", "KASHA", "Gikondo Industrial Zone, KK 15 Rd", -1.9905, 30.0745, creds);
        createBranch(kasha, "Industrial Zone", "KASHA-IND", "Gikondo Industrial Zone, KK 15 Rd", -1.9905, 30.0745, creds);

        // ── Patients (each with a default GPS location so they can order) ───
        seedPatient("aline.patient@demo.rw", "Aline Uwase", "Home — Nyamirambo", -1.9800, 30.0400);
        seedPatient("eric.patient@demo.rw", "Eric Niyonzima", "Home — Remera", -1.9580, 30.1120);
        creds.add(new String[]{"—", "Patient", "aline.patient@demo.rw"});
        creds.add(new String[]{"—", "Patient", "eric.patient@demo.rw"});

        // ── Credentials block ───────────────────────────────────────────────
        log.info("══════════════════════════════════════════════════════════════");
        log.info("DEMO ACCOUNTS SEEDED — shared password for every account: {}", DEMO_PASSWORD);
        log.info("--------------------------------------------------------------");
        for (String[] c : creds) {
            log.info(String.format("%-34s | %-16s | %s", c[0], c[1], c[2]));
        }
        log.info("══════════════════════════════════════════════════════════════");
    }

    /** Creates an ACTIVE pharmacy and its single Pharmacy Admin (MANAGER). */
    private Pharmacy createPharmacy(String name, String code, String address, double lat, double lon, List<String[]> creds) {
        Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
                .name(name)
                .pharmacyCode(code)
                .licenseNumber("RPC-" + code)
                .contactInfo("+250 78" + (1000000 + Math.abs(code.hashCode()) % 9000000))
                .address(address)
                .latitude(lat)
                .longitude(lon)
                .status(PharmacyStatus.ACTIVE)
                .build());

        User adminUser = saveUser("admin." + code.toLowerCase() + "@demo.rw", "Admin " + name, UserRole.MANAGER);
        ManagerProfile mp = ManagerProfile.builder()
                .user(adminUser).pharmacy(pharmacy).activatedAt(LocalDateTime.now()).build();
        pharmacy.setManagerProfile(mp);
        pharmacy = pharmacyRepository.save(pharmacy); // cascade ALL persists the manager profile

        creds.add(new String[]{name, "Pharmacy Admin", adminUser.getEmail()});
        return pharmacy;
    }

    /** Creates an ACTIVE branch under a pharmacy, with a branch manager and one ACTIVE pharmacist. */
    private void createBranch(Pharmacy pharmacy, String area, String branchCode, String address,
                              double lat, double lon, List<String[]> creds) {
        Branch branch = branchRepository.save(Branch.builder()
                .name(pharmacy.getName() + " — " + area)
                .address(address)
                .latitude(lat)
                .longitude(lon)
                .contactInfo(pharmacy.getContactInfo())
                .status(BranchStatus.ACTIVE)
                .pharmacy(pharmacy)
                .build());

        String key = branchCode.toLowerCase();
        User bmUser = saveUser("branch." + key + "@demo.rw", "Branch Mgr " + area, UserRole.BRANCH_MANAGER);
        branchManagerProfileRepository.save(BranchManagerProfile.builder()
                .user(bmUser).branch(branch).activatedAt(LocalDateTime.now()).build());

        User phUser = saveUser("pharm." + key + "@demo.rw", "Pharmacist " + area, UserRole.PHARMACIST);
        pharmacistRepository.save(PharmacistProfile.builder()
                .pharmacistUniqueId(branchCode + "-PH01")
                .user(phUser).pharmacy(pharmacy).branch(branch).build());

        creds.add(new String[]{pharmacy.getName() + " — " + area, "Branch Manager", bmUser.getEmail()});
        creds.add(new String[]{pharmacy.getName() + " — " + area, "Pharmacist", phUser.getEmail()});
    }

    // ── Phase 2: inventory (separately guarded, back-fills empty demo branches) ──

    private void seedInventoryIfMissing() {
        List<Medicine> catalog = ensureCatalog();

        List<Pharmacy> demoPharmacies = pharmacyRepository.findAll().stream()
                .filter(p -> p.getPharmacyCode() != null && DEMO_CODES.contains(p.getPharmacyCode()))
                .toList();

        int stockedBranches = 0;
        for (Pharmacy pharmacy : demoPharmacies) {
            for (Branch branch : branchRepository.findByPharmacyId(pharmacy.getId())) {
                if (!inventoryRepository.findByBranchId(branch.getId()).isEmpty()) {
                    continue; // already stocked — leave it untouched
                }
                stockBranch(pharmacy, branch, catalog);
                stockedBranches++;
            }
        }

        if (stockedBranches == 0) {
            log.info("Demo inventory already present — skipping inventory seeding");
        } else {
            log.info("Demo inventory seeded for {} branch(es)", stockedBranches);
        }
    }

    /** Stocks every catalog medicine into a branch (PharmacyInventory + one StockEntry batch). */
    private void stockBranch(Pharmacy pharmacy, Branch branch, List<Medicine> catalog) {
        boolean belvedere = "BELVEDERE".equals(pharmacy.getPharmacyCode());
        for (Medicine m : catalog) {
            int qty = 100;
            // One deliberately low-stock item so the low-stock alert is demoable.
            if (belvedere && m.getName().startsWith("Paracetamol")) qty = 5;

            BigDecimal price = m.getSellingPrice() != null ? m.getSellingPrice() : BigDecimal.ZERO;
            LocalDate expiry = LocalDate.now().plusYears(1);
            int threshold = m.getLowStockAlert() != null ? m.getLowStockAlert() : 20;

            inventoryRepository.save(PharmacyInventory.builder()
                    .pharmacy(pharmacy)
                    .branch(branch)
                    .medicine(m)
                    .quantity(qty)
                    .price(price)
                    .unit(m.getUnit())
                    .lowStockThreshold(threshold)
                    .expiryDate(expiry)
                    .build());

            stockEntryRepository.save(StockEntry.builder()
                    .medicine(m)
                    .branch(branch)
                    .batchNumber("DEMO-" + branch.getId() + "-" + m.getId())
                    .quantityReceived(qty)
                    .purchasePrice(price)
                    .supplier("Demo Supplier Ltd")
                    .manufacturingDate(LocalDate.now().minusMonths(1))
                    .expiryDate(expiry)
                    .notes("Seeded demo stock")
                    .build());
        }
        log.info("Stocked branch '{}' (id={}) with {} medicines", branch.getName(), branch.getId(), catalog.size());
    }

    /** Find-or-create the demo medicine catalogue (idempotent by name). */
    private List<Medicine> ensureCatalog() {
        return List.of(
                ensureMedicine("Vitamin C 1000mg", "Ascorbic Acid", false, "Vitamins & Supplements", "Tablets", "2500", 20),
                ensureMedicine("Paracetamol 500mg", "Paracetamol", false, "Pain Relievers", "Tablets", "1000", 20),
                ensureMedicine("Amoxicillin 250mg", "Amoxicillin", true, "Antibiotics", "Capsules", "3500", 15),
                ensureMedicine("ORS Rehydration Salts", "Oral Rehydration Salts", false, "Digestive Medicines", "Sachets", "800", 15),
                ensureMedicine("Ibuprofen 400mg", "Ibuprofen", false, "Pain Relievers", "Tablets", "1500", 20),
                ensureMedicine("Cetirizine 10mg", "Cetirizine", false, "Antihistamines", "Tablets", "1200", 20),
                ensureMedicine("Omeprazole 20mg", "Omeprazole", false, "Digestive Medicines", "Capsules", "1800", 15)
        );
    }

    private Medicine ensureMedicine(String name, String generic, boolean rx, String category,
                                    String unit, String price, int lowStockAlert) {
        return medicineRepository.findByNameIgnoreCase(name).orElseGet(() ->
                medicineRepository.save(Medicine.builder()
                        .name(name)
                        .genericName(generic)
                        .requiresPrescription(rx)
                        .category(category)
                        .unit(unit)
                        .sellingPrice(new BigDecimal(price))
                        .lowStockAlert(lowStockAlert)
                        .description(name + " (demo)")
                        .build()));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private User saveUser(String email, String fullName, UserRole role) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(email)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .role(role)
                .isActive(true)
                .isVerified(true)
                .build());
    }

    private void seedPatient(String email, String fullName, String locationLabel, double lat, double lon) {
        User user = saveUser(email, fullName, UserRole.PATIENT);
        PatientProfile profile = PatientProfile.builder()
                .user(user)
                .gender("Other")
                .build();
        PatientLocation loc = PatientLocation.builder()
                .label(locationLabel)
                .latitude(lat)
                .longitude(lon)
                .inputType(LocationInputType.GPS)
                .isDefault(true)
                .patientProfile(profile)
                .build();
        profile.getLocations().add(loc);
        patientProfileRepository.save(profile); // cascade persists the default location
    }
}
