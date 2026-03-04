package com.darexsh.revancedupdater;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExampleUnitTest {

    @Test
    public void compareVersions_returnsZeroForEqualVersions() {
        assertEquals(0, VersionUtils.compareVersions("20.14.43", "20.14.43"));
    }

    @Test
    public void compareVersions_handlesDifferentLengthVersions() {
        assertEquals(0, VersionUtils.compareVersions("1.2", "1.2.0"));
        assertTrue(VersionUtils.compareVersions("1.2.1", "1.2") > 0);
    }

    @Test
    public void compareVersions_handlesTextSuffixes() {
        assertEquals(0, VersionUtils.compareVersions("1.2-beta", "1.2"));
        assertTrue(VersionUtils.compareVersions("2.0-rc1", "1.9.9") > 0);
    }

    @Test
    public void compareVersions_handlesNullValues() {
        assertEquals(0, VersionUtils.compareVersions(null, null));
        assertTrue(VersionUtils.compareVersions("1.0", null) > 0);
        assertTrue(VersionUtils.compareVersions(null, "1.0") < 0);
    }
}
