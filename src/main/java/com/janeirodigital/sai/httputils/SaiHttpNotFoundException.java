package com.janeirodigital.sai.httputils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Custom exception thrown when an HTTP resource cannot be found
 */
@Getter @AllArgsConstructor
public class SaiHttpNotFoundException extends Exception {
    private final String message;
}
