package com.undatech.opaque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class RdpCertificateTrustTest {
    private static final String SHA256 = "88:46:f0:78:a6:e8:13:a5:a5:9f:99:a2:81:1d:91:b4:c2:9e:b6:12:1e:67:a6:41:9f:9a:2b:14:46:5c:fd:85";
    private static final String SHA1 = "eb:87:a7:f3:18:26:4f:c3:55:a0:77:3a:c0:6a:aa:c6:8f:93:10:1f";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private String pem() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/rdp-trust-test.pem")) {
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    private File records(String records) throws Exception {
        File file = temporary.newFile();
        Files.writeString(file.toPath(), records, StandardCharsets.UTF_8);
        return file;
    }

    private String record(String fingerprint) {
        return "desktop.example 3389 " + fingerprint + " c3ViamVjdA== aXNzdWVy\n";
    }

    @Test public void displaysSha256OfTheCertificateInsteadOfPem() throws Exception {
        assertEquals(SHA256, RdpCertificateTrust.displayFingerprint(pem(), true));
        assertEquals(SHA256, RdpCertificateTrust.displayFingerprint(SHA256, false));
        assertNull(RdpCertificateTrust.displayFingerprint("broken certificate", true));
    }

    @Test public void migratesExactHostPortAndCertificateSha256() throws Exception {
        File file = records(record(SHA256.toUpperCase(Locale.ROOT)));
        assertTrue(RdpCertificateTrust.matchesKnownHosts(file, "desktop.example", 3389, pem(), true));
        assertFalse(RdpCertificateTrust.matchesKnownHosts(file, "other.example", 3389, pem(), true));
        assertFalse(RdpCertificateTrust.matchesKnownHosts(file, "desktop.example", 3390, pem(), true));
    }

    @Test public void supportsOlderSha1PinsOnlyWhenTheCertificateMatches() throws Exception {
        File file = records(record(SHA1));
        assertTrue(RdpCertificateTrust.matchesKnownHosts(file, "desktop.example", 3389, pem(), true));
        assertFalse(RdpCertificateTrust.matchesKnownHosts(file, "desktop.example", 3389, SHA256, false));
    }

    @Test public void changedPinNeverMigrates() throws Exception {
        File file = records(record("00".repeat(32)));
        assertFalse(RdpCertificateTrust.matchesKnownHosts(file, "desktop.example", 3389, pem(), true));
    }

    @Test public void conflictingDuplicatePinsNeverMigrate() throws Exception {
        File file = records(record(SHA256) + record("00".repeat(32)));
        assertFalse(RdpCertificateTrust.matchesKnownHosts(file, "desktop.example", 3389, pem(), true));
    }

    @Test public void hostOnlyLegacyOrMalformedRecordsRequireConfirmation() throws Exception {
        assertFalse(RdpCertificateTrust.matchesKnownHosts(records("desktop.example " + SHA256),
                "desktop.example", 3389, pem(), true));
        assertFalse(RdpCertificateTrust.matchesKnownHosts(records("desktop.example 3389 " + SHA256),
                "desktop.example", 3389, pem(), true));
        assertFalse(RdpCertificateTrust.matchesKnownHosts(records(record(SHA256)),
                "desktop.example", 3389, "broken certificate", true));
    }

    @Test public void missingFileDoesNotGrantTrust() throws Exception {
        assertFalse(RdpCertificateTrust.matchesKnownHosts(new File(temporary.getRoot(), "missing"),
                "desktop.example", 3389, pem(), true));
    }

    @Test public void exactNonPemFingerprintCanMigrate() throws Exception {
        assertTrue(RdpCertificateTrust.matchesKnownHosts(records(record(SHA256)),
                "desktop.example", 3389, SHA256.toUpperCase(Locale.ROOT), false));
    }
}
