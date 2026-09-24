package market.sisu.common;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The single error response body used by every endpoint (contracts/openapi.yaml {@code Error}).
 *
 * @param code stable machine-readable code the frontend maps to Finnish copy
 * @param message English diagnostic text; never contains stack traces or internals
 */
@Schema(
        name = "Error",
        requiredProperties = {"code", "message"})
public record ApiError(String code, String message) {}
