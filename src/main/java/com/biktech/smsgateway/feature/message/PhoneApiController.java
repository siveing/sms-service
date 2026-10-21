package com.biktech.smsgateway.feature.message;

import com.biktech.smsgateway.common.ApiResponse;
import com.biktech.smsgateway.config.OpenApiConfig;
import com.biktech.smsgateway.feature.caller.CallerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/phone")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.DEVICE_KEY)
@Tag(name = "Phone", description = "For the Android phones: take waiting messages, text them, report back. Sign with X-Device-Key")
public class PhoneApiController {

    private final MessageService service;

    @PostMapping("/pull")
    @Operation(summary = "Take the next waiting messages; each is held for this phone until the lease runs out")
    public ApiResponse<List<MessageDtos.PhoneMessage>> pull(
            @Parameter(hidden = true) @AuthenticationPrincipal CallerPrincipal phone,
            @Valid @RequestBody(required = false) MessageDtos.PullRequest request) {
        return ApiResponse.ok(service.pull(phone.id(), request));
    }

    @PostMapping("/report")
    @Operation(summary = "Report one message as sent, or failed so it is tried again")
    public ApiResponse<Void> report(
            @Parameter(hidden = true) @AuthenticationPrincipal CallerPrincipal phone,
            @Valid @RequestBody MessageDtos.ReportRequest request) {
        service.report(phone.id(), phone.name(), request);
        return ApiResponse.ok(null);
    }
}
