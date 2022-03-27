package com.janeirodigital.sai.httputils;

/**
 * General exception used to represent issues processing HTTP requests and responses
 */
public class SaiHttpException extends Exception {
    public SaiHttpException(String message, Throwable cause) {
        super(message, cause);
    }
    public SaiHttpException(String message) {
        super(message);
    }
}
