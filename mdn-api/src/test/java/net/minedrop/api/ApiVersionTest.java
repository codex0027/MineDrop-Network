package net.minedrop.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiVersionTest {

    @Test
    @DisplayName("Parse '1.2.3' correctly")
    void parseFull() {
        ApiVersion v = ApiVersion.parse("1.2.3");
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getPatch());
    }

    @Test
    @DisplayName("Parse '1.0' (no patch) correctly")
    void parseNoPatch() {
        ApiVersion v = ApiVersion.parse("1.0");
        assertEquals(1, v.getMajor());
        assertEquals(0, v.getMinor());
        assertEquals(0, v.getPatch());
    }

    @Test
    @DisplayName("isCompatibleWith: same major + higher minor = compatible")
    void compatibleHigherMinor() {
        ApiVersion current = ApiVersion.parse("1.5.0");
        ApiVersion required = ApiVersion.parse("1.3.0");
        assertTrue(current.isCompatibleWith(required));
    }

    @Test
    @DisplayName("isCompatibleWith: same major + same minor = compatible")
    void compatibleSameMinor() {
        ApiVersion current = ApiVersion.parse("1.3.0");
        ApiVersion required = ApiVersion.parse("1.3.0");
        assertTrue(current.isCompatibleWith(required));
    }

    @Test
    @DisplayName("isCompatibleWith: different major = incompatible")
    void incompatibleDifferentMajor() {
        ApiVersion current = ApiVersion.parse("1.5.0");
        ApiVersion required = ApiVersion.parse("2.0.0");
        assertFalse(current.isCompatibleWith(required));
    }

    @Test
    @DisplayName("isCompatibleWith: lower minor = incompatible")
    void incompatibleLowerMinor() {
        ApiVersion current = ApiVersion.parse("1.2.0");
        ApiVersion required = ApiVersion.parse("1.5.0");
        assertFalse(current.isCompatibleWith(required));
    }

    @Test
    @DisplayName("compareTo: 1.0.0 < 1.1.0")
    void compareTo() {
        assertTrue(ApiVersion.parse("1.0.0").compareTo(ApiVersion.parse("1.1.0")) < 0);
        assertTrue(ApiVersion.parse("2.0.0").compareTo(ApiVersion.parse("1.0.0")) > 0);
        assertEquals(0, ApiVersion.parse("1.0.0").compareTo(ApiVersion.parse("1.0.0")));
    }

    @Test
    @DisplayName("Invalid format throws")
    void invalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("abc"));
    }

    @Test
    @DisplayName("CURRENT version is parseable")
    void currentVersionExists() {
        assertNotNull(ApiVersion.CURRENT);
        assertTrue(ApiVersion.CURRENT.getMajor() > 0);
    }
}
