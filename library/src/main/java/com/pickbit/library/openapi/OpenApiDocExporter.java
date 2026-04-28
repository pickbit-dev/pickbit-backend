package com.pickbit.library.openapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
public class OpenApiDocExporter {

    private final Environment env;

    public OpenApiDocExporter(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void export() {
        if (isProductionProfile()) {
            log.info("[OpenApiDocExporter] skipped on production profile");
            return;
        }
        try {
            int port = env.getProperty("local.server.port", Integer.class, 8080);
            String serviceName = env.getProperty("spring.application.name", "service");

            String url = "http://localhost:" + port + "/v3/api-docs.yaml";

            byte[] bytes = RestClient.create()
                    .get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);

            Path outputPath = Path.of("openapi", serviceName, "openapi.yaml");
            Files.createDirectories(outputPath.getParent());
            assert bytes != null;
            Files.write(outputPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("[OpenApiDocExporter] OpenAPI spec saved: {}", outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("[OpenApiDocExporter] Failed to save OpenAPI spec: {}", e.getMessage());
        }
    }

    private boolean isProductionProfile() {
        for (String profile : env.getActiveProfiles()) {
            if ("deploy".equals(profile) || "prod".equals(profile) || "production".equals(profile)) {
                return true;
            }
        }
        return false;
    }
}
