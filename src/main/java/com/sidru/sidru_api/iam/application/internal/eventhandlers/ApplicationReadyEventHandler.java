package com.sidru.sidru_api.iam.application.internal.eventhandlers;

import com.sidru.sidru_api.iam.domain.model.commands.SeedRolesCommand;
import com.sidru.sidru_api.iam.domain.model.commands.SignUpCommand;
import com.sidru.sidru_api.iam.domain.model.entities.Role;
import com.sidru.sidru_api.iam.domain.model.valueobjects.Roles;
import com.sidru.sidru_api.iam.domain.services.RoleCommandService;
import com.sidru.sidru_api.iam.domain.services.UserCommandService;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

/**
 * Seeds the roles and the default admin user at application startup.
 * Runs once — guarded by existsByEmail.
 */
@Service
public class ApplicationReadyEventHandler {

    private final RoleCommandService roleCommandService;
    private final UserCommandService userCommandService;
    private final UserRepository userRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationReadyEventHandler.class);

    public ApplicationReadyEventHandler(RoleCommandService roleCommandService,
                                        UserCommandService userCommandService,
                                        UserRepository userRepository) {
        this.roleCommandService = roleCommandService;
        this.userCommandService = userCommandService;
        this.userRepository = userRepository;
    }

    @EventListener
    @Order(10)
    public void on(ApplicationReadyEvent event) {
        var applicationName = event.getApplicationContext().getId();
        LOGGER.info("IAM seeding started for {} at {}", applicationName, currentTimestamp());

        roleCommandService.handle(new SeedRolesCommand());
        seedAdminUser();

        LOGGER.info("IAM seeding finished for {} at {}", applicationName, currentTimestamp());
    }

    private void seedAdminUser() {
        final String adminEmail = "admin@sidru.pe";
        if (userRepository.existsByEmail(adminEmail)) return;

        var adminRole = new Role(Roles.ROLE_ADMIN);
        var command = new SignUpCommand(
                "SIDRU Administrator",
                adminEmail,
                "Admin1234",
                null,
                "Lima",
                List.of(adminRole)
        );
        userCommandService.handle(command);
        LOGGER.info("Admin user seeded — email: {} / password: Admin1234", adminEmail);
    }

    private Timestamp currentTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }
}
