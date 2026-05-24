package com.clipboardsync.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClipboardWatcherTest {

    @Test
    void treatsFirstTextValueAsBaseline() {
        FakeClipboardService clipboardService = new FakeClipboardService();
        clipboardService.writeText("already copied");
        ClipboardWatcher watcher = new ClipboardWatcher(clipboardService, Duration.ofMillis(500));

        assertThat(watcher.poll()).isEmpty();
    }

    @Test
    void emitsChangedTextOnce() {
        FakeClipboardService clipboardService = new FakeClipboardService();
        clipboardService.writeText("one");
        ClipboardWatcher watcher = new ClipboardWatcher(clipboardService, Duration.ofMillis(500));
        watcher.poll();

        clipboardService.writeText("two");

        assertThat(watcher.poll()).contains("two");
        assertThat(watcher.poll()).isEmpty();
    }

    @Test
    void markObservedSuppressesNextMatchingPoll() {
        FakeClipboardService clipboardService = new FakeClipboardService();
        ClipboardWatcher watcher = new ClipboardWatcher(clipboardService, Duration.ofMillis(500));

        clipboardService.writeText("remote text");
        watcher.markObserved("remote text");

        assertThat(watcher.poll()).isEmpty();
    }

    @Test
    void ignoresEmptyClipboardText() {
        FakeClipboardService clipboardService = new FakeClipboardService();
        ClipboardWatcher watcher = new ClipboardWatcher(clipboardService, Duration.ofMillis(500));

        clipboardService.writeText("");

        assertThat(watcher.poll()).isEmpty();
    }

    @Test
    void rejectsNonPositivePollInterval() {
        assertThatThrownBy(() -> new ClipboardWatcher(new FakeClipboardService(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    private static class FakeClipboardService implements ClipboardService {

        private String text;

        @Override
        public Optional<String> readText() {
            return text == null || text.isEmpty() ? Optional.empty() : Optional.of(text);
        }

        @Override
        public void writeText(String text) {
            this.text = text;
        }
    }
}
