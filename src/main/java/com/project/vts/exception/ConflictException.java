// src/main/java/com/project/vts/exception/ConflictException.java
package com.project.vts.exception;

/** 409 — xung đột trạng thái (vd đã là bạn / đã có lời mời / lời mời không còn ở trạng thái chờ). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
