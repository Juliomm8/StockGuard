package com.stockguard.api.rest.internal;

import javax.ws.rs.core.Application;

import org.osgi.service.component.annotations.Component;

/** Aplicación publicada por el JAX-RS Whiteboard de OSGi. */
@Component(
        service = Application.class,
        property = {
                "osgi.jaxrs.application.base=/api",
                "osgi.jaxrs.name=stockguard"
        }
)
public class StockGuardApplication extends Application {
}
