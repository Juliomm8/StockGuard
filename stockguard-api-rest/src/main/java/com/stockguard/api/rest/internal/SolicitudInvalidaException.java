package com.stockguard.api.rest.internal;

import javax.ws.rs.BadRequestException;

/**
 * Solicitud rechazada por el cliente (HTTP 400) con un mensaje seguro para
 * mostrarle. La causa técnica queda adjunta solo para depuración.
 */
final class SolicitudInvalidaException extends BadRequestException {

    private static final long serialVersionUID = 1L;

    SolicitudInvalidaException(String mensajePublico, Throwable causa) {
        super(mensajePublico, causa);
    }
}
