package com.volmit.plague.util;

public class PlagueTypeException extends Exception {
    public PlagueTypeException() {
            super("Unknown type exception");
}

    public PlagueTypeException(String message) {
        super(message);
    }
}
