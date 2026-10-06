package io.floci.gcp.core.common;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ProjectNumbers {

    private static final BigInteger RANGE = BigInteger.valueOf(900_000_000_000L);

    private ProjectNumbers() {}

    // A 12-digit number derived from SHA-256 rather than String.hashCode, whose 32-bit space lets
    // two project IDs share a number (for example "project-an" and "project-c0").
    public static String of(String projectId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(projectId.getBytes(StandardCharsets.UTF_8));
            return String.valueOf(100_000_000_000L + new BigInteger(1, digest).mod(RANGE).longValue());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
