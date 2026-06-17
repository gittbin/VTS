// src/main/java/com/project/vts/exception/ForbiddenException.java
package com.project.vts.exception;

/** 403 — đã đăng nhập nhưng không có quyền thao tác trên tài nguyên này (vd IDOR trên friendship). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
