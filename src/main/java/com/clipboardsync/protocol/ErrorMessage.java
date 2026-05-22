package com.clipboardsync.protocol;

/**
 * Error message returned to a client when a WebSocket payload is rejected.
 *
 * @param type always {@link MessageType#ERROR}
 * @param code stable machine-readable error code
 * @param message human-readable error summary that never includes secrets
 */
public record ErrorMessage(
        MessageType type,
        String code,
        String message
) {
    /**
     * Creates a protocol error response.
     *
     * @param code stable machine-readable error code
     * @param message human-readable error summary
     * @return error message
     */
    public static ErrorMessage of(String code, String message) {
        return new ErrorMessage(MessageType.ERROR, code, message);
    }
}
