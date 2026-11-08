package com.stockguard.api.rest.internal;

import com.stockguard.inventario.api.InventarioConsultaService;
import com.stockguard.inventario.api.InventarioMovimientosService;
import com.stockguard.inventario.api.InventarioService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contrato HTTP de la propiedad {@code cantidad}: solo se aceptan enteros JSON.
 * Se usa el mismo provider Jackson que publica el bundle y servicios simulados,
 * por lo que no se accede a PostgreSQL.
 */
@DisplayName("Contrato HTTP de las cantidades")
class CantidadRequestHttpTest {

    private Server servidor;
    private HttpClient cliente;
    private String baseUrl;
    private InventarioService inventario;
    private InventarioMovimientosService movimientos;

    @BeforeEach
    void iniciarServidorHttp() throws IOException {
        int puerto = puertoDisponible();
        inventario = mock(InventarioService.class);
        movimientos = mock(InventarioMovimientosService.class);

        ProductosResource recurso = new ProductosResource();
        recurso.vincularInventario(inventario);
        recurso.vincularMovimientos(movimientos);
        recurso.vincularConsultas(mock(InventarioConsultaService.class));

        JAXRSServerFactoryBean fabrica = new JAXRSServerFactoryBean();
        fabrica.setAddress("http://127.0.0.1:" + puerto + "/");
        fabrica.setServiceBeans(List.of(recurso));
        fabrica.setProviders(List.of(
                new StockGuardJsonProvider(),
                new RestExceptionMapper()
        ));
        servidor = fabrica.create();
        cliente = HttpClient.newHttpClient();
        baseUrl = "http://127.0.0.1:" + puerto + "/productos/P-001/";
    }

    @AfterEach
    void detenerServidorHttp() {
        servidor.stop();
        servidor.destroy();
    }

    @Test
    @DisplayName("Rechaza una cantidad decimal con HTTP 400")
    void rechazaCantidadDecimal() throws Exception {
        HttpResponse<String> respuesta = publicar("entradas", "{\"cantidad\": 5.9}");

        assertRechazada(respuesta);
        verifyNoInteractions(movimientos, inventario);
    }

    @Test
    @DisplayName("Rechaza un decimal sin parte fraccionaria, como 5.0")
    void rechazaDecimalEntero() throws Exception {
        assertRechazada(publicar("salidas", "{\"cantidad\": 5.0}"));
        verifyNoInteractions(movimientos, inventario);
    }

    @Test
    @DisplayName("Rechaza una cantidad enviada como texto con HTTP 400")
    void rechazaCantidadComoTexto() throws Exception {
        HttpResponse<String> respuesta = publicar("entradas", "{\"cantidad\": \"5\"}");

        assertRechazada(respuesta);
        verifyNoInteractions(movimientos, inventario);
    }

    @ParameterizedTest(name = "JSON inválido: {0}")
    @ValueSource(strings = {
            "{\"cantidad\": ",
            "{\"cantidad\" 5}",
            "no es json",
            "{\"cantidad\": true}",
            "{\"cantidad\": [5]}",
            "{\"cantidad\": 99999999999}"
    })
    @DisplayName("Rechaza JSON malformado o de tipo incorrecto con HTTP 400")
    void rechazaJsonInvalido(String cuerpo) throws Exception {
        assertRechazada(publicar("ajustes", cuerpo));
        verifyNoInteractions(movimientos, inventario);
    }

    @Test
    @DisplayName("Rechaza cantidad nula, cuerpo vacío y propiedades desconocidas")
    void rechazaCuerposIncompletos() throws Exception {
        assertRechazada(publicar("entradas", "{\"cantidad\": null}"));
        assertRechazada(publicar("entradas", "{}"));
        assertRechazada(publicar("entradas", ""));
        assertRechazada(publicar("entradas", "{\"cantidad\": 5, \"extra\": 1}"));
        verifyNoInteractions(movimientos, inventario);
    }

    @Test
    @DisplayName("Entrada con cantidad cero responde 400")
    void rechazaEntradaConCero() throws Exception {
        HttpResponse<String> respuesta = publicar("entradas", "{\"cantidad\": 0}");

        assertRechazada(respuesta);
        assertTrue(respuesta.body().contains("mayor que cero"));
        verifyNoInteractions(movimientos);
    }

    @Test
    @DisplayName("Salida con cantidad cero responde 400")
    void rechazaSalidaConCero() throws Exception {
        HttpResponse<String> respuesta = publicar("salidas", "{\"cantidad\": 0}");

        assertRechazada(respuesta);
        assertTrue(respuesta.body().contains("mayor que cero"));
        verifyNoInteractions(movimientos);
    }

    @Test
    @DisplayName("Ajuste con cantidad negativa responde 400")
    void rechazaAjusteNegativo() throws Exception {
        assertRechazada(publicar("ajustes", "{\"cantidad\": -1}"));
        verifyNoInteractions(inventario);
    }

    @Test
    @DisplayName("Ajuste con cantidad cero es válido y responde 204")
    void aceptaAjusteConCero() throws Exception {
        when(inventario.actualizarExistencias("P-001", 0)).thenReturn(true);

        HttpResponse<String> respuesta = publicar("ajustes", "{\"cantidad\": 0}");

        assertEquals(204, respuesta.statusCode());
        verify(inventario).actualizarExistencias("P-001", 0);
    }

    @Test
    @DisplayName("Entrada con un entero válido responde 204")
    void aceptaEntradaEnteraValida() throws Exception {
        when(movimientos.registrarEntrada("P-001", 5)).thenReturn(true);

        HttpResponse<String> respuesta = publicar("entradas", "{\"cantidad\": 5}");

        assertEquals(204, respuesta.statusCode());
        assertTrue(respuesta.body().isEmpty());
        verify(movimientos).registrarEntrada("P-001", 5);
    }

    @Test
    @DisplayName("Salida con un entero válido responde 204")
    void aceptaSalidaEnteraValida() throws Exception {
        when(movimientos.registrarSalida("P-001", 2)).thenReturn(true);

        assertEquals(204, publicar("salidas", "{\"cantidad\": 2}").statusCode());
        verify(movimientos).registrarSalida("P-001", 2);
    }

    private void assertRechazada(HttpResponse<String> respuesta) {
        assertEquals(400, respuesta.statusCode(), respuesta.body());
        assertTrue(
                respuesta.headers().firstValue("Content-Type").orElse("")
                        .contains("application/json"),
                "El error debe ser JSON"
        );
        assertTrue(respuesta.body().contains("\"mensaje\""), respuesta.body());
        assertFalse(respuesta.body().contains("java."), respuesta.body());
        assertFalse(respuesta.body().contains("jackson"), respuesta.body());
        assertFalse(respuesta.body().contains("\tat "), respuesta.body());
    }

    private HttpResponse<String> publicar(String recurso, String cuerpo)
            throws IOException, InterruptedException {
        return cliente.send(
                HttpRequest.newBuilder(URI.create(baseUrl + recurso))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private int puertoDisponible() throws IOException {
        try (ServerSocket socket = new ServerSocket(
                0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }
}
