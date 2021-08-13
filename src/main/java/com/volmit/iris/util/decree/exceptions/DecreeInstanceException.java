package com.volmit.iris.util.decree.exceptions;

/**
 * Thrown when classes are instantiated that fail because of a missing or faulty decree component
 */
public class DecreeInstanceException extends Exception {
    public DecreeInstanceException(String message){
        super(message);
    }
}
