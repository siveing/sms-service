package com.biktech.smsgateway.config;

import com.biktech.smsgateway.security.ApiKeyAuthenticationFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String API_KEY = "apiKey";
    public static final String DEVICE_KEY = "deviceKey";

    @Bean
    public OpenAPI smsGatewayOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("SMS Gateway")
                .version("1.0.0")
                .description("Apps queue SMS with an API key; Android phones take them, text them and report back."))
            .components(new Components()
                .addSecuritySchemes(API_KEY, header(ApiKeyAuthenticationFilter.API_KEY_HEADER))
                .addSecuritySchemes(DEVICE_KEY, header(ApiKeyAuthenticationFilter.DEVICE_KEY_HEADER)));
    }

    private static SecurityScheme header(String name) {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(name);
    }
}
