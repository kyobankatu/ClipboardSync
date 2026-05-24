package com.clipboardsync.client.autostart;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeCommandResolverTest {

    @Test
    void prefersBundledLauncherPathWhenAvailable() {
        RuntimeCommandResolver resolver = new RuntimeCommandResolver(Map.of(
                RuntimeCommandResolver.LAUNCHER_PATH_ENV,
                "/opt/ClipboardSync/bin/clipboardsync"
        ));

        AutostartCommand command = resolver.resolve();

        assertThat(command.arguments())
                .containsExactly("/opt/ClipboardSync/bin/clipboardsync", "client", "sync");
    }
}
