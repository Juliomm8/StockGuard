package com.stockguard.api.rest.internal;

import com.stockguard.api.rest.internal.dto.ErrorDto;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("Mapeo seguro de errores REST")
class RestExceptionMapperTest {

    private final RestExceptionMapper mapper = new RestExceptionMapper();

    @Test
    @DisplayName("Conserva el estado de un error HTTP sin devolver detalles internos")
    void conservaEstadoHttpYOcultaDetalle() {
        WebApplicationException error = new WebApplicationException(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity("jdbc.password=secreto")
                        .build()
        );

        try (Response respuesta = mapper.toResponse(error)) {
            assertEquals(
                    Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                    respuesta.getStatus()
            );
            assertInstanceOf(ErrorDto.class, respuesta.getEntity());
            assertFalse(respuesta.getEntity().toString().contains("secreto"));
        }
    }
}
