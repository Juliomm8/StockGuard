package com.stockguard.api.rest.internal;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * Provider Jackson asociado explícitamente a la aplicación REST de StockGuard.
 *
 * <p>Aplica una lectura estricta de los cuerpos JSON: un entero debe llegar
 * como número entero, sin decimales ni comillas, y el JSON malformado se
 * informa como una solicitud inválida (HTTP 400). La configuración vive solo
 * en esta instancia, por lo que no afecta a otros servicios de Karaf.
 */
@Provider
@Component(
        service = {MessageBodyReader.class, MessageBodyWriter.class},
        scope = ServiceScope.PROTOTYPE,
        property = {
                "osgi.jaxrs.extension=true",
                "osgi.jaxrs.application.select=(osgi.jaxrs.name=stockguard)",
                "osgi.jaxrs.media.type=application/json",
                "osgi.jaxrs.name=stockguard-json"
        }
)
public class StockGuardJsonProvider extends JacksonJaxbJsonProvider {

    static final String MENSAJE_CUERPO_INVALIDO =
            "El cuerpo de la solicitud debe ser un JSON válido con una "
            + "cantidad entera, por ejemplo {\"cantidad\": 5}.";

    public StockGuardJsonProvider() {
        setMapper(crearMapper());
    }

    /** Mapper estricto para enteros; se crea por instancia y no se comparte. */
    static ObjectMapper crearMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.coercionConfigFor(LogicalType.Integer)
                // 5.9 o 5.0 no se truncan a 5.
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                // "5" y "" no se convierten en 5 ni en null.
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        return mapper;
    }

    @Override
    public Object readFrom(
            Class<Object> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders,
            InputStream entityStream
    ) throws IOException {
        try {
            return super.readFrom(
                    type,
                    genericType,
                    annotations,
                    mediaType,
                    httpHeaders,
                    entityStream
            );
        } catch (InvalidDefinitionException error) {
            // El tipo destino está mal definido: es un fallo del servidor.
            throw error;
        } catch (StreamReadException | JsonMappingException error) {
            // Sintaxis incorrecta, tipos que no coinciden o números fuera de
            // rango: el cliente envió un cuerpo inválido.
            throw new SolicitudInvalidaException(MENSAJE_CUERPO_INVALIDO, error);
        }
    }
}
