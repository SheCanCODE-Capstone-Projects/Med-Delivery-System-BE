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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds demo USER ACCOUNTS (and the pharmacies/branches they belong to) so the
 * app can be tested/demoed without going through the email-based invitation and
 * OTP flows. Every account is created active/verified with a shared known
 * password — log in and use the system as normal (add stock, place orders, etc.).
 *
 * Opt-in only: set app.seed.demo.enabled=true (env APP_SEED_DEMO_ENABLED=true).
 * Idempotent: skips if the demo data already exists.
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
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.demo.enabled:false}")
    private boolean demoEnabled;

    /** Shared password for every seeded demo account (documented for the defense). */
    private static final String DEMO_PASSWORD = "Demo@1234";
    private static final String MARKER_CODE = "KIPHARMA"; // pharmacy code used to detect prior seeding

    @Override
    @Transactional
    public void run(String... args) {
        if (!demoEnabled) {
            log.info("Demo seeding disabled (app.seed.demo.enabled=false) — skipping DemoDataSeeder");
            return;
        }
        boolean alreadySeeded = pharmacyRepository.findAll().stream()
                .anyMatch(p -> MARKER_CODE.equals(p.getPharmacyCode()));
        if (alreadySeeded) {
            log.info("Demo data already exists — skipping DemoDataSeeder");
            return;
        }
        try {
            seedDemo();
        } catch (Exception e) {
            log.error("Demo seeding failed: {}", e.getMessage(), e);
        }
    }

    private void seedDemo() {
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

    // ── builders ─────────────────────────────────────────────────────────────

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
