package com.clipboardsync.client.autostart;

import java.util.List;

/**
 * Stable command used by the operating system to start {@code client sync}.
 *
 * @param arguments process arguments, including the executable path
 */
public record AutostartCommand(List<String> arguments) {
}
