package com.shopflow.user;

import com.shopflow.security.Role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Admins cannot self-register (that would be a privilege-escalation hole), so the first
 * admin is created at startup from ADMIN_EMAIL / ADMIN_PASSWORD environment variables.
 * The operation is idempotent: if the account already exists, nothing happens.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.email:}") String adminEmail,
                                   @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set; skipping admin bootstrap");
            return;
        }
        if (adminPassword.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD is shorter than {} characters; admin account NOT created",
                    MIN_ADMIN_PASSWORD_LENGTH);
            return;
        }
        String email = User.normalizeEmail(adminEmail);
        if (userRepository.existsByEmail(email)) {
            log.info("Admin bootstrap: account {} already exists", email);
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(adminPassword), "Platform Admin", Role.ADMIN));
        log.info("Admin bootstrap: created admin account {}", email);
    }
}
