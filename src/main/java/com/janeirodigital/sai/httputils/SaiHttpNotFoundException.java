package com.janeirodigital.sai.httputils;

/**
 * Custom exception thrown when an HTTP resource cannot be found
 */
public class SaiHttpNotFoundException extends Exception {
    public SaiHttpNotFoundException(String message) {
        super(message);
    }
}
