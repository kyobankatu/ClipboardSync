package com.clipboardsync;

import com.clipboardsync.config.ClipboardSyncProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Starts the ClipboardSync relay server.
 *
 * <p>The application only relays encrypted clipboard payloads. Clipboard plaintext is expected to
 * be encrypted and decrypted by clients before and after transport.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ClipboardSyncProperties.class)
public class ClipboardSyncApplication {

    /**
     * Creates the application bootstrap instance used by Spring Boot.
     */
    public ClipboardSyncApplication() {
    }

    /**
     * Runs the Spring Boot application.
     *
     * @param args command-line arguments passed by the runtime
     */
    public static void main(String[] args) {
        SpringApplication.run(ClipboardSyncApplication.class, args);
    }
}
