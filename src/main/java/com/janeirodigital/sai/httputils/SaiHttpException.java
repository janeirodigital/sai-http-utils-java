package com.janeirodigital.sai.httputils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * General exception used to represent issues processing HTTP requests and responses
 */
@Getter @AllArgsConstructor
public class SaiHttpException extends Exception {
    private final String message;
}
