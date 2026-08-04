package net.minedrop.bridge.api;

/**
 * API exposed by MDN-Bridge for other plugins to verify their security context.
 */
public interface BridgeSecurityProvider {

    /**
     * Checks whether a plugin is verified and running in a secure context.
     *
     * @param pluginId unique name of the plugin (e.g. "MDN-SAM")
     * @return true if the plugin's signature is valid and active
     */
    boolean isPluginSecure(String pluginId);

    /**
     * Returns the decrypted session token for inter-server communication.
     */
    String getActiveSessionToken();
}
