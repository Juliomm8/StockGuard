package com.stockguard.api.rest.internal;

import com.stockguard.inventario.api.InventarioConsultaService;
import com.stockguard.inventario.api.InventarioMovimientosService;
import com.stockguard.inventario.api.InventarioService;
import com.stockguard.inventario.api.MovimientoInventario;
import com.stockguard.inventario.api.Producto;
import com.stockguard.inventario.api.TipoMovimiento;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.endpoint.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Integración HTTP de la API REST de StockGuard")
class ProductosResourceIntegrationTest {

    private Server servidor;
    private HttpClient cliente;
    private String baseUrl;
    private InventarioService inventario;
    private InventarioMovimientosService movimientos;
    private InventarioConsultaService consultas;

    @BeforeEach
    void iniciarServidorHttp() throws IOException {
        int puerto = puertoDisponible();
        inventario = mock(InventarioService.class);
        movimientos = mock(InventarioMovimientosService.class);
        consultas = mock(InventarioConsultaService.class);

        ProductosResource recurso = new ProductosResource();
        recurso.vincularInventario(inventario);
        recurso.vincularMovimientos(movimientos);
        recurso.vincularConsultas(consultas);

        JAXRSServerFactoryBean fabrica = new JAXRSServerFactoryBean();
        fabrica.setAddress("http://127.0.0.1:" + puerto + "/");
        fabrica.setServiceBeans(List.of(recurso));
        fabrica.setProviders(List.of(
                new StockGuardJsonProvider(),
                new RestExceptionMapper()
        ));
        servidor = fabrica.create();
        cliente = HttpClient.newHttpClient();
        baseUrl = "http://127.0.0.1:" + puerto + "/productos";
    }

    @AfterEach
    void detenerServidorHttp() {
        if (servidor != null) {
            servidor.stop();
            servidor.destroy();
        }
    }

    @Test
    @DisplayName("Sirve productos JSON por HTTP usando el contrato OSGi")
    void sirveCatalogoComoJson() throws Exception {
        when(inventario.listarProductos()).thenReturn(List.of(
                new Producto("P-001", "Teclado mecánico Logitech", 12)
        ));

        HttpResponse<String> respuesta = cliente.send(
                HttpRequest.newBuilder(URI.create(baseUrl))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.headers()
                .firstValue("Content-Type")
                .orElse("")
                .contains("application/json"));
        assertTrue(respuesta.body().contains("P-001"));
        assertTrue(respuesta.body().contains("Teclado mecánico Logitech"));
        assertTrue(respuesta.body().contains("\"cantidadDisponible\":12"));
    }

    @Test
    @DisplayName("Serializa un producto individual y sus movimientos como JSON")
    void sirveProductoYMovimientosComoJson() throws Exception {
        when(consultas.buscarProducto("P-001"))
                .thenReturn(Optional.of(
                        new Producto("P-001", "Teclado mecánico Logitech", 12)
                ));
        when(consultas.buscarMovimientos("P-001"))
                .thenReturn(Optional.of(List.of(new MovimientoInventario(
                        7L,
                        "P-001",
                        TipoMovimiento.ENTRADA,
                        4,
                        8,
                        12,
                        Instant.parse("2026-11-08T12:00:00Z"),
                        "Reposición"
                ))));

        HttpResponse<String> producto = cliente.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/P-001"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        HttpResponse<String> movimientosProducto = cliente.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/P-001/movimientos"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, producto.statusCode());
        assertTrue(producto.headers()
                .firstValue("Content-Type")
                .orElse("")
                .contains("application/json"));
        assertTrue(producto.body().contains("P-001"));
        assertTrue(producto.body().contains("Teclado mecánico Logitech"));
        assertTrue(producto.body().contains("cantidadDisponible"));
        assertTrue(producto.body().contains("12"));

        assertEquals(200, movimientosProducto.statusCode());
        assertTrue(movimientosProducto.headers()
                .firstValue("Content-Type")
                .orElse("")
                .contains("application/json"));
        assertTrue(movimientosProducto.body().contains("ENTRADA"));
        assertTrue(movimientosProducto.body().contains("existenciaPosterior"));
        assertTrue(movimientosProducto.body().contains("12"));
        assertTrue(movimientosProducto.body().contains("Reposición"));
    }

    @Test
    @DisplayName("Rechaza por HTTP los códigos mayores que la columna de PostgreSQL")
    void rechazaCodigoMayorQueLongitudPersistida() throws Exception {
        String codigo = "A".repeat(33);

        HttpResponse<String> respuesta = cliente.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/" + codigo))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, respuesta.statusCode());
        assertTrue(respuesta.body().contains("mensaje"));
        verifyNoInteractions(inventario, movimientos, consultas);
    }

    @Test
    @DisplayName("Recibe una entrada HTTP y responde sin acceder a persistencia REST propia")
    void registraEntradaPorHttp() throws Exception {
        when(movimientos.registrarEntrada("P-001", 3)).thenReturn(true);

        HttpResponse<String> respuesta = cliente.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/P-001/entradas"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"cantidad\":3}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(204, respuesta.statusCode());
        assertTrue(respuesta.body().isEmpty());
        verify(movimientos).registrarEntrada("P-001", 3);
    }

    @Test
    @DisplayName("Devuelve errores HTTP seguros para una cantidad inválida")
    void respondeErroresHttpControlados() throws Exception {
        HttpResponse<String> respuesta = cliente.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/P-001/entradas"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"cantidad\":0}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, respuesta.statusCode());
        assertTrue(respuesta.body().contains("mensaje"));
        assertTrue(!respuesta.body().contains("java.lang"));
    }

    private int puertoDisponible() throws IOException {
        try (ServerSocket socket = new ServerSocket(
                0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }
}
