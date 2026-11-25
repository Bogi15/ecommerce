package com.shop.ecommerce.common.openapi;

import com.shop.ecommerce.common.exception.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "E-commerce Shop API",
                version = "1.0",
                description = "REST API of the e-commerce shop. Validation errors use the ApiError body; "
                        + "malformed JSON is reported as Problem Details."),
        tags = {
                @Tag(name = "Auth", description = "Register, log in, refresh and revoke tokens"),
                @Tag(name = "Users", description = "The logged-in user's profile"),
                @Tag(name = "Products", description = "Browse the catalog; admins manage products"),
                @Tag(name = "Categories", description = "Browse categories; admins create them"),
                @Tag(name = "Inventory", description = "Stock management (admin only)"),
                @Tag(name = "Cart", description = "The logged-in user's shopping cart"),
                @Tag(name = "Orders", description = "Place, view and cancel orders"),
                @Tag(name = "Payments", description = "Pay for orders through Stripe"),
                @Tag(name = "Payment webhook", description = "Stripe event notifications (called by Stripe only)")
        },
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    private static final String API_ERROR = "ApiError";
    private static final String UNAUTHORIZED = "401";
    private static final String FORBIDDEN = "403";
    private static final String JSON = "application/json";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenApiCustomizer errorResponsesCustomizer() {
        return openApi -> {
            registerApiErrorSchema(openApi);

            openApi.getPaths().values().stream()
                    .flatMap(pathItem -> pathItem.readOperations().stream())
                    .forEach(this::documentErrorResponses);
        };
    }

    private void registerApiErrorSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }

        Schema<?> schema = ModelConverters.getInstance(true).readAllAsResolvedSchema(ApiError.class).schema;
        openApi.getComponents().addSchemas(API_ERROR, schema);
    }

    private void documentErrorResponses(Operation operation) {
        operation.getResponses().forEach(this::documentErrorBody);

        if (requiresLogin(operation) && !operation.getResponses().containsKey(UNAUTHORIZED)) {
            operation.getResponses().addApiResponse(UNAUTHORIZED,
                    new ApiResponse().description("Missing, invalid or expired access token"));
        }
    }

    private boolean requiresLogin(Operation operation) {
        return operation.getSecurity() == null;
    }

    private void documentErrorBody(String code, ApiResponse response) {
        if (!isError(code) || hasProblemDetails(response)) {
            return;
        }

        response.setContent(FORBIDDEN.equals(code) ? null : apiErrorContent());
    }

    private boolean isError(String code) {
        return code.startsWith("4") || code.startsWith("5");
    }

    private boolean hasProblemDetails(ApiResponse response) {
        return response.getContent() != null && response.getContent().containsKey(PROBLEM_JSON);
    }

    private Content apiErrorContent() {
        Schema<?> reference = new Schema<>().$ref("#/components/schemas/" + API_ERROR);
        return new Content().addMediaType(JSON, new MediaType().schema(reference));
    }
}
