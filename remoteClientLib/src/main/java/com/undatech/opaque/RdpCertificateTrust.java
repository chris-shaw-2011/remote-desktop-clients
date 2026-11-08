package com.undatech.opaque;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

/** Narrow migration of FreeRDP 2's host-and-port certificate pins to FreeRDP 3. */
final class RdpCertificateTrust {
    private RdpCertificateTrust() {}

    static String displayFingerprint(String fingerprint, boolean pem) {
        if (!pem) return fingerprint;
        try {
            return digest(certificate(fingerprint), "SHA-256", true);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    static boolean matchesKnownHosts(File knownHosts, String host, long port,
                                     String fingerprint, boolean pem) {
        if (host == null || port < 1 || port > 65535) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(knownHosts), StandardCharsets.UTF_8))) {
            X509Certificate cert = pem ? certificate(fingerprint) : null;
            String sha256 = cert != null ? digest(cert, "SHA-256", false) : normalize(fingerprint);
            String sha1 = cert != null ? digest(cert, "SHA-1", false) : null;
            boolean found = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length == 0 || !host.equals(fields[0])) continue;
                if (fields.length < 2) return false;
                long storedPort;
                try {
                    storedPort = Long.parseLong(fields[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
                if (port != storedPort) continue;
                // known_hosts2: host port fingerprint base64(subject) base64(issuer).
                if (fields.length != 5) return false;
                String stored = normalize(fields[2]);
                if (stored == null || (!stored.equals(sha256) && !stored.equals(sha1))) return false;
                found = true;
            }
            return found;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static X509Certificate certificate(String pem) throws GeneralSecurityException {
        if (pem == null) throw new IllegalArgumentException("Missing certificate");
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String digest(X509Certificate cert, String algorithm, boolean colons)
            throws GeneralSecurityException {
        byte[] bytes = MessageDigest.getInstance(algorithm).digest(cert.getEncoded());
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            if (colons && result.length() > 0) result.append(':');
            result.append(Character.forDigit((value & 0xff) >>> 4, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static String normalize(String fingerprint) {
        if (fingerprint == null) return null;
        String normalized = fingerprint.replace(":", "").toLowerCase(Locale.ROOT);
        return normalized.matches("[0-9a-f]{40}|[0-9a-f]{64}") ? normalized : null;
    }
}
