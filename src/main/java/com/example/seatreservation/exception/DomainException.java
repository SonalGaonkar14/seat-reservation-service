package com.example.seatreservation.exception;

public class DomainException extends RuntimeException {
    private final int status;
    private final String reason;

    public DomainException(int status, String reason, String message) {
        super(message);
        this.status = status;
        this.reason = reason;
    }

    public int getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
