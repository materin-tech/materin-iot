/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
