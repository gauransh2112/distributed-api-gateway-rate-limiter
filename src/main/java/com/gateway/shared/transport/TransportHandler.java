package com.gateway.shared.transport;

/**
 * Functional contract for processing a transport-decoupled gateway request.
 *
 * <p>Allows any external interface layer (Servlet Filter, Spring MVC Controller,
 * gRPC Server handler) to invoke the core Gateway engine without exposing
 * framework-specific request/response types.</p>
 */
@FunctionalInterface
public interface TransportHandler {

    /**
     * Dispatches a transport request through the Gateway pipeline.
     *
     * @param request the normalized transport request
     * @return the resulting transport response
     */
    TransportResponse handle(TransportRequest request);
}
