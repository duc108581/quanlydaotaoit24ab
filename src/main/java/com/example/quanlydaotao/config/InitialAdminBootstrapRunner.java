package com.example.quanlydaotao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.quanlydaotao.service.InitialAdminBootstrapService;

@Component
public class InitialAdminBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(InitialAdminBootstrapRunner.class);

    private final InitialAdminBootstrapService bootstrapService;
    private final String username;
    private final String displayName;
    private final String password;

    public InitialAdminBootstrapRunner(
            InitialAdminBootstrapService bootstrapService,
            @Value("${app.bootstrap.admin.username:}") String username,
            @Value("${app.bootstrap.admin.display-name:}") String displayName,
            @Value("${app.bootstrap.admin.password:}") String password) {
        this.bootstrapService = bootstrapService;
        this.username = username;
        this.displayName = displayName;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && displayName.isBlank() && password.isBlank()) {
            return;
        }
        if (username.isBlank() || displayName.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Configure bootstrap admin username, display name, and password together.");
        }

        boolean created = bootstrapService.createFirstAdmin(username, displayName, password);
        if (created) {
            logger.info("Created the initial ADMIN account in the configured database. Remove bootstrap credentials.");
        } else {
            logger.info("Initial ADMIN bootstrap skipped because an active ADMIN account already exists.");
        }
    }
}
