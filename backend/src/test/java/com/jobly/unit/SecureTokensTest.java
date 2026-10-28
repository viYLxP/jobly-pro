package com.jobly.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.common.SecureTokens;

class SecureTokensTest {

    @Test
    @DisplayName("tokens are 43 URL-safe characters and never repeat")
    void generate() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = SecureTokens.generate();
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(seen.add(token)).isTrue();
        }
    }

    @Test
    @DisplayName("sha256 matches the published test vector for \"abc\"")
    void sha256Vector() {
        assertThat(SecureTokens.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
