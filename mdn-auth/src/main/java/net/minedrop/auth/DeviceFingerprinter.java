package net.minedrop.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Creates device fingerprints from client connection metadata.
 * <p>
 * Combines client brand, protocol version, and a per-player salt into a
 * SHA-256 hash that can be used to identify the same physical device across
 * accounts (useful for alt detection).
 * <p>
 * The fingerprint is NOT a precise hardware ID — it's a heuristic based on
 * what Velocity sees during the handshake. It's effective for flagging
 * suspicious patterns but not cryptographically guaranteed.
 */
public final class DeviceFingerprinter {

    /**
     * Creates a device fingerprint for a connecting player.
     *
     * @param playerUuid      the player's UUID (used as salt)
     * @param ipAddress       the player's IP address
     * @param clientBrand     the client brand string (e.g., "vanilla", "fabric")
     * @param protocolVersion the Minecraft protocol version (e.g., 766)
     * @return a Fingerprint object containing the hash and raw components
     */
    public Fingerprint create(UUID playerUuid, String ipAddress,
                               String clientBrand, int protocolVersion) {
        // Normalize inputs
        String brand = (clientBrand != null) ? clientBrand.toLowerCase().trim() : "unknown";
        String ip = (ipAddress != null) ? ipAddress : "0.0.0.0";

        // Build a composite key: brand + protocol + ip (first 3 octets only for IPv4 privacy)
        // Note: playerUuid is NOT included — fingerprints must be consistent across
        // different accounts on the same device for alt detection to work.
        String ipPrefix = extractIpPrefix(ip);
        String composite = brand + "|" + protocolVersion + "|" + ipPrefix;

        // Hash with SHA-256
        String hash = sha256(composite);

        return new Fingerprint(hash, brand, protocolVersion, ipPrefix);
    }

    /**
     * Extracts the first 3 octets of an IPv4 address for partial fingerprinting.
     * Example: "192.168.1.100" → "192.168.1"
     */
    private String extractIpPrefix(String ip) {
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot);
        }
        return ip;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── Data classes ──

    /** A device fingerprint with its computed hash and raw components. */
    public static class Fingerprint {
        private final String hash;
        private final String clientBrand;
        private final int protocolVersion;
        private final String ipPrefix;

        public Fingerprint(String hash, String clientBrand, int protocolVersion, String ipPrefix) {
            this.hash = hash;
            this.clientBrand = clientBrand;
            this.protocolVersion = protocolVersion;
            this.ipPrefix = ipPrefix;
        }

        /** The SHA-256 fingerprint hash. */
        public String getHash() { return hash; }

        /** The normalized client brand string. */
        public String getClientBrand() { return clientBrand; }

        /** The Minecraft protocol version. */
        public int getProtocolVersion() { return protocolVersion; }

        /** The first 3 octets of the IP address. */
        public String getIpPrefix() { return ipPrefix; }

        @Override
        public String toString() {
            return "Fingerprint{hash=" + hash.substring(0, 12) + "..."
                    + ", brand=" + clientBrand
                    + ", proto=" + protocolVersion
                    + ", ip=" + ipPrefix + "}";
        }
    }
}
