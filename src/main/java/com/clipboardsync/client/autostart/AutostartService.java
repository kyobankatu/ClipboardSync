package com.clipboardsync.client.autostart;

/**
 * Installs, removes, and inspects login autostart for the clipboard sync client.
 */
public interface AutostartService {

    /**
     * Installs the current packaged client as a login autostart entry.
     *
     * @throws Exception if the operating system rejects the autostart registration
     */
    void install() throws Exception;

    /**
     * Removes the login autostart entry.
     *
     * @throws Exception if the operating system rejects the removal
     */
    void uninstall() throws Exception;

    /**
     * Returns a human-readable autostart status.
     *
     * @return status text suitable for CLI output
     * @throws Exception if the status cannot be checked
     */
    String status() throws Exception;
}
