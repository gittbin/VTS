// src/main/java/com/project/vts/controller/CallHistoryController.java
package com.project.vts.controller;

import com.project.vts.dto.response.CallHistoryResponse;
import com.project.vts.service.CallHistoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/calls")
public class CallHistoryController {

    private final CallHistoryService callHistoryService;

    public CallHistoryController(CallHistoryService callHistoryService) {
        this.callHistoryService = callHistoryService;
    }

    @GetMapping
    public List<CallHistoryResponse> myCalls(Authentication authentication) {
        return callHistoryService.historyForUsername(authentication.getName());
    }
}