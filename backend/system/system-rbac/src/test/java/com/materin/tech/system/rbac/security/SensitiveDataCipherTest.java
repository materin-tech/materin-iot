package com.materin.tech.system.rbac.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataCipherTest {

    private final SensitiveDataCipher cipher =
            new SensitiveDataCipher("unit-test-key-for-sensitive-data-32b");

    @Test
    @DisplayName("AES-GCM 加解密往返一致")
    void encryptDecryptRoundtrip() {
        String idNo = "110101199001011234";
        String encrypted = cipher.encrypt(idNo);
        assertThat(encrypted).isNotEqualTo(idNo);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(idNo);
    }

    @Test
    @DisplayName("相同明文两次加密产生不同密文（随机 IV）")
    void encrypt_randomIv() {
        String encrypted1 = cipher.encrypt("110101199001011234");
        String encrypted2 = cipher.encrypt("110101199001011234");
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("身份证号脱敏：保留前 3 后 4")
    void mask_idCard() {
        assertThat(SensitiveDataCipher.mask("110101199001011234", 3, 4))
                .isEqualTo("110***********1234");
    }

    @Test
    @DisplayName("护照号脱敏：保留前 2 后 2")
    void mask_passport() {
        assertThat(SensitiveDataCipher.mask("E12345678", 2, 2))
                .isEqualTo("E1*****78");
    }
}
