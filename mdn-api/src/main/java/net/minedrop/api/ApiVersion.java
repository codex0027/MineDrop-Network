package net.minedrop.api;

/**
 * Defines the current API version for compatibility checking.
 * <p>
 * All MDN plugins declare their {@code requiredApiVersion} in plugin.yml.
 * MDN-Bridge validates that each plugin's requirement is satisfied by the
 * currently loaded API version before allowing the plugin to enable.
 *
 * <h3>Versioning Policy</h3>
 * <ul>
 *   <li><b>MAJOR</b> — Bumped on breaking API changes (e.g. method removal, signature change)</li>
 *   <li><b>MINOR</b> — Bumped on new features (backward-compatible additions)</li>
 *   <li><b>PATCH</b> — Bumped on bug fixes (no API surface changes)</li>
 * </ul>
 *
 * A plugin requiring {@code 1.0.0} will work with any {@code 1.x.x} API.
 * A plugin requiring {@code 2.0.0} will NOT work with a {@code 1.x.x} API.
 */
public final class ApiVersion implements Comparable<ApiVersion> {

    /** Current API version — update this on every release. */
    public static final ApiVersion CURRENT = new ApiVersion(1, 0, 0);

    private final int major;
    private final int minor;
    private final int patch;

    public ApiVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * Parses a version string like "1.2.3" into an ApiVersion.
     */
    public static ApiVersion parse(String version) {
        String[] parts = version.trim().split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid version format: " + version
                    + " (expected MAJOR.MINOR[.PATCH])");
        }
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        return new ApiVersion(major, minor, patch);
    }

    /**
     * Returns true if the given required version is compatible with the current API.
     * A plugin requiring 1.0.0 works with API 1.5.3 but NOT with API 2.0.0.
     */
    public boolean isCompatibleWith(ApiVersion required) {
        return this.major == required.major && this.minor >= required.minor;
    }

    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }

    @Override
    public int compareTo(ApiVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiVersion that)) return false;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return (major * 1_000_000) + (minor * 1_000) + patch;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
