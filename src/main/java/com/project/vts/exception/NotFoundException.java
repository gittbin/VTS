// src/main/java/com/project/vts/exception/NotFoundException.java
package com.project.vts.exception;

/** 404 — tài nguyên không tồn tại (vd lời mời đã bị huỷ). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
