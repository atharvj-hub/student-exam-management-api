package com.internship.student_exam_api.seeder;

import com.internship.student_exam_api.entity.AppUser;
import com.internship.student_exam_api.enums.Role;
import com.internship.student_exam_api.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapSeeder implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String adminInitialPassword;

    @Override
    public void run(String... args) throws Exception {
        // Find if any active admin exists (password not equal to *DISABLED*)
        long activeAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .filter(u -> !u.getPassword().equals("*DISABLED*"))
                .count();

        if (activeAdmins > 0) {
            log.info("Active admin user already exists. Skipping admin bootstrap.");
            return;
        }

        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminInitialPassword)) {
            log.warn("No active admin user found and ADMIN_EMAIL / ADMIN_INITIAL_PASSWORD are not set. " +
                     "The system currently has no accessible administrator account.");
            return;
        }

        log.info("Bootstrapping new admin user: {}", adminEmail);
        AppUser admin = new AppUser();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminInitialPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("Admin bootstrap complete.");
    }
}
