package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    @Test
    void hashesPasswordsWithUniqueSaltsAndVerifiesOnlyMatchingPassword() {
        String firstHash = PasswordHasher.hash("Workshop123!");
        String secondHash = PasswordHasher.hash("Workshop123!");

        assertNotEquals(firstHash, secondHash);
        assertTrue(PasswordHasher.verify("Workshop123!", firstHash));
        assertFalse(PasswordHasher.verify("wrong", firstHash));
    }

    @Test
    void rejectsMalformedHashes() {
        assertFalse(PasswordHasher.verify("password", "not-a-password-hash"));
        assertFalse(PasswordHasher.verify("password", "pbkdf2-sha256$invalid$salt$hash"));
        assertFalse(PasswordHasher.verify(null, PasswordHasher.hash("password")));
        assertFalse(PasswordHasher.verify(
                "password",
                "pbkdf2-sha256$1$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        ));
        assertFalse(PasswordHasher.verify(
                "password",
                "pbkdf2-sha256$210000$AA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        ));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }
}
