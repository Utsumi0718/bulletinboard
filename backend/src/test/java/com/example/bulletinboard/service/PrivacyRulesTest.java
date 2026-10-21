package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrivacyRulesTest {
    private final String testKey=Base64.getEncoder().encodeToString(new byte[32]);
    @ParameterizedTest @ValueSource(strings={"a@withdrawn.invalid","a@INVALID","a@sub.InVaLiD"})
    void reservedDomainsAreCaseInsensitive(String email) { assertThat(WithdrawalIdentity.reservedEmail(email)).isTrue(); }
    @ParameterizedTest @ValueSource(strings={"withdrawn_user_1","WITHDRAWN_USER_99"})
    void reservedNamesAreCaseInsensitive(String username) { assertThat(WithdrawalIdentity.reservedUsername(username)).isTrue(); }
    @ParameterizedTest @ValueSource(strings={"a@example.com","a@invalid.example"})
    void otherDomainsRemainAvailable(String email) { assertThat(WithdrawalIdentity.reservedEmail(email)).isFalse(); }
    @Test void encryptionRoundTripsOnlyWithCorrectContext() {
        var crypto=new PrivacyCrypto(testKey,"test");byte[] input="fictional identity".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] encrypted=crypto.encrypt(input,"subject-1");
        assertThat(new String(encrypted,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("fictional identity");
        assertThat(crypto.decrypt(encrypted,"subject-1","test")).isEqualTo(input);
    }
    @Test void eachEncryptionUsesDistinctNonce() {
        var crypto=new PrivacyCrypto(testKey,"test");
        assertThat(crypto.encrypt(new byte[]{1},"context")).isNotEqualTo(crypto.encrypt(new byte[]{1},"context"));
    }
    @Test void changingContextRejectsCiphertext() {
        var crypto=new PrivacyCrypto(testKey,"test");
        assertThatThrownBy(() -> crypto.decrypt(crypto.encrypt(new byte[]{1},"first"),"second","test"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void changingKeyRejectsCiphertext() {
        byte[] other=new byte[32];other[0]=1;
        var first=new PrivacyCrypto(testKey,"test");var second=new PrivacyCrypto(Base64.getEncoder().encodeToString(other),"test");
        assertThatThrownBy(() -> second.decrypt(first.encrypt(new byte[]{1},"same"),"same","test"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void missingKeyFailsClosed() {
        assertThatThrownBy(() -> new PrivacyCrypto("","test").encrypt(new byte[]{1},"same"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Privacy encryption failed");
    }
}
