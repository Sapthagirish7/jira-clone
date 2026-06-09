package com.jira.infrastructure.config;

import com.jira.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup, ensure the seeded admin user has a correctly-encoded password.
 * This runs after Flyway, so the user row already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserJpaRepository userRepo;
    private final PasswordEncoder   passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepo.findByUsername("admin").ifPresent(admin -> {
            String freshHash = passwordEncoder.encode("admin123");
            admin.setPasswordHash(freshHash);
            userRepo.save(admin);
            log.info("Admin password hash refreshed on startup.");
        });
    }
}
