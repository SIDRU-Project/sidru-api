package com.sidru.sidru_api.notifications.infrastructure.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Inicializa Firebase (FCM) solo si {@code sidru.firebase.enabled=true} y el fichero de
 * service-account existe. Si falta el fichero, {@code @ConditionalOnResource} salta esta
 * config y no se crea el bean {@link FirebaseMessaging}: el backend arranca igual y el
 * adapter queda no-op.
 */
@Configuration
@ConditionalOnProperty(name = "sidru.firebase.enabled", havingValue = "true")
@ConditionalOnResource(resources = "${sidru.firebase.credentials-path}")
public class FirebaseConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseConfig.class);

    private final ResourceLoader resourceLoader;

    @Value("${sidru.firebase.credentials-path}")
    private String credentialsPath;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        Resource resource = resourceLoader.getResource(credentialsPath);
        try (InputStream credentials = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            LOGGER.info("Firebase initialized for FCM (service-account credentials loaded from {})",
                    credentialsPath);
            return FirebaseMessaging.getInstance(app);
        }
    }
}
