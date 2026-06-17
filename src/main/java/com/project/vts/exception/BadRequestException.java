// src/main/java/com/project/vts/exception/BadRequestException.java
package com.project.vts.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}