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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.API_KEY)
@Tag(name = "Messages", description = "For apps: queue an SMS and follow it. Sign with X-Api-Key")
public class MessageApiController {

    private final MessageService service;

    @PostMapping
    @Operation(summary = "Queue one message: a real SMS from a phone, or a Telegram post if this app is set to Telegram")
    public ApiResponse<MessageDtos.MessageView> send(
            @Parameter(hidden = true) @AuthenticationPrincipal CallerPrincipal app,
            @Valid @RequestBody MessageDtos.SendRequest request) {
        return ApiResponse.ok(service.send(app.id(), app.name(), app.channel(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Where one of your SMS stands")
    public ApiResponse<MessageDtos.MessageView> status(
            @Parameter(hidden = true) @AuthenticationPrincipal CallerPrincipal app,
            @PathVariable UUID id) {
        return ApiResponse.ok(service.status(app.id(), id));
    }
}
