package com.biktech.smsgateway.feature.caller;

import com.biktech.smsgateway.feature.message.DeliveryChannel;

import java.util.UUID;

/** Who is calling the API, as the key named them, and where an app's messages go. */
public record CallerPrincipal(UUID id, String name, CallerKind kind, DeliveryChannel channel) {
}
