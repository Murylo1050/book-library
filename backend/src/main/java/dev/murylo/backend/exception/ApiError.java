package dev.murylo.backend.exception;

public record ApiError(
        int status,
        String message
) {}
