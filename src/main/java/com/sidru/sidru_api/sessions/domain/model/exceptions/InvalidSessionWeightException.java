package com.sidru.sidru_api.sessions.domain.model.exceptions;

public class InvalidSessionWeightException extends RuntimeException {
    public InvalidSessionWeightException() {
        super("Invalid session weight");
    }
}
