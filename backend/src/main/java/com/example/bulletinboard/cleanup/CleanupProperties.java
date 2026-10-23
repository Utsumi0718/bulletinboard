package com.example.bulletinboard.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;

/** Disabled until an operator has verified isolated MySQL tests and dry-run results. */
@Component
@ConfigurationProperties(prefix = "topic.cleanup")
@Getter @Setter
public class CleanupProperties {
    private boolean enabled;
    private int dailyLimit = 100;
    private int transactionTimeoutSeconds = 30;
    private int leaseSeconds = 300;
    private int heartbeatSeconds = 60;
    private int lockWaitSeconds = 5;

    @PostConstruct
    void validate() {
        if (dailyLimit < 1 || transactionTimeoutSeconds < 1 || lockWaitSeconds < 1
                || heartbeatSeconds < 1 || leaseSeconds <= heartbeatSeconds
                || leaseSeconds <= transactionTimeoutSeconds + lockWaitSeconds) {
            throw new IllegalArgumentException("Invalid topic cleanup configuration");
        }
    }
}
