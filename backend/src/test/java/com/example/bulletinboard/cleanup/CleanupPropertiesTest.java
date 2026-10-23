package com.example.bulletinboard.cleanup;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CleanupPropertiesTest {
    @Test void initialConfigurationIsDisabledAndMatchesApprovedTiming(){var p=new CleanupProperties();p.validate();assertThat(p.isEnabled()).isFalse();assertThat(p.getDailyLimit()).isEqualTo(100);assertThat(p.getLeaseSeconds()).isEqualTo(300);assertThat(p.getHeartbeatSeconds()).isEqualTo(60);assertThat(p.getTransactionTimeoutSeconds()).isEqualTo(30);}
    @ParameterizedTest @ValueSource(ints={0,-1}) void invalidDailyLimitIsRejected(int n){var p=new CleanupProperties();p.setDailyLimit(n);assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class);}
    @Test void heartbeatCannotBeLongerThanLease(){var p=new CleanupProperties();p.setHeartbeatSeconds(300);assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class);}
    @Test void deletionCannotUseUpWholeLease(){var p=new CleanupProperties();p.setTransactionTimeoutSeconds(300);assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class);}
    @Test void zeroLockWaitIsRejected(){var p=new CleanupProperties();p.setLockWaitSeconds(0);assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class);}
}
