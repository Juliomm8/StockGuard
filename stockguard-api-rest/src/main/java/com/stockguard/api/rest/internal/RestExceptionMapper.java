package com.stockguard.api.rest.internal;

import com.stockguard.api.rest.internal.dto.ErrorDto;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Convierte fallos no tratados en respuestas seguras y consistentes. */
@Provider
@Component(
        service = ExceptionMapper.class,
        scope = ServiceScope.PROTOTYPE,
        property = {
                "osgi.jaxrs.extension=true",
                "osgi.jaxrs.application.select=(osgi.jaxrs.name=stockguard)"
        }
)
public class RestExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RestExceptionMapper.class);

    @Override
    public Response toResponse(Exception error) {
        if (error instanceof SolicitudInvalidaException) {
            return respuesta(Response.Status.BAD_REQUEST, error.getMessage());
        }
        if (error instanceof BadRequestException) {
            return respuesta(Response.Status.BAD_REQUEST,
                    "La solicitud no tiene un formato válido.");
        }
        if (error instanceof NotSupportedException) {
            return respuesta(Response.Status.UNSUPPORTED_MEDIA_TYPE,
                    "El tipo de contenido no está soportado.");
        }
        if (error instanceof WebApplicationException webError) {
            int estado = webError.getResponse().getStatus();
            if (estado >= 400 && estado < 600) {
                String mensaje;
                if (estado == Response.Status.NOT_FOUND.getStatusCode()) {
                    mensaje = "No se encontró el recurso solicitado.";
                } else if (estado < 500) {
                    mensaje = "La solicitud no pudo procesarse.";
                } else {
                    mensaje = "No fue posible procesar la solicitud.";
                }
                return respuesta(estado, mensaje);
            }
        }

        LOGGER.error("Error inesperado al procesar una solicitud REST.", error);
        return respuesta(
                Response.Status.INTERNAL_SERVER_ERROR,
                "No fue posible procesar la solicitud."
        );
    }

    private Response respuesta(Response.Status estado, String mensaje) {
        return respuesta(estado.getStatusCode(), mensaje);
    }

    private Response respuesta(int estado, String mensaje) {
        return Response.status(estado)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorDto(mensaje))
                .build();
    }
}
