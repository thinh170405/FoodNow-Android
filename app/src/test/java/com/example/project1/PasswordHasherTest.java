package com.example.project1;
import org.junit.Test;
import static org.junit.Assert.*;
public class PasswordHasherTest {
    @Test public void correctPasswordMatchesAndWrongOneFails() {
        String encoded=PasswordHasher.hash("Matkhau123!");
        assertTrue(PasswordHasher.verify("Matkhau123!",encoded));
        assertFalse(PasswordHasher.verify("matkhau123!",encoded));
        assertFalse(encoded.contains("Matkhau123!"));
    }
    @Test public void independentSaltsAndInvalidData() {
        assertNotEquals(PasswordHasher.hash("Matkhau123!"),PasswordHasher.hash("Matkhau123!"));
        assertFalse(PasswordHasher.verify("test", ""));
        assertFalse(PasswordHasher.verify("test", "invalid:data:hash"));
    }
}
