package com.stockguard.api.rest.internal;

import com.stockguard.api.rest.internal.dto.CantidadRequest;
import com.stockguard.api.rest.internal.dto.ErrorDto;
import com.stockguard.inventario.api.InventarioConsultaService;
import com.stockguard.inventario.api.InventarioMovimientosService;
import com.stockguard.inventario.api.InventarioNoDisponibleException;
import com.stockguard.inventario.api.InventarioService;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cada tipo de fallo debe producir su propio estado HTTP: validación (400),
 * producto inexistente (404), conflicto de existencias (409), servicio no
 * disponible (503) y fallo interno (500), sin exponer detalles técnicos.
 */
@DisplayName("Clasificación de errores del recurso REST")
class ProductosResourceErroresTest {

    private static final String DETALLE_INTERNO = "detalle-interno-secreto";

    private InventarioService inventario;
    private InventarioMovimientosService movimientos;
    private InventarioConsultaService consultas;
    private ProductosResource recurso;

    @BeforeEach
    void prepararRecurso() {
        inventario = mock(InventarioService.class);
        movimientos = mock(InventarioMovimientosService.class);
        consultas = mock(InventarioConsultaService.class);
        recurso = new ProductosResource();
        recurso.vincularInventario(inventario);
        recurso.vincularMovimientos(movimientos);
        recurso.vincularConsultas(consultas);
    }

    /** Una operación del recurso, para recorrerlas todas con los mismos casos. */
    private record Operacion(String nombre, Supplier<Response> ejecutar) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    private Operacion[] operaciones() {
        CantidadRequest cantidad = new CantidadRequest(3);
        return new Operacion[] {
                new Operacion("listar", () -> recurso.listarProductos()),
                new Operacion("buscar", () -> recurso.buscarProducto("P-001")),
                new Operacion("movimientos", () -> recurso.buscarMovimientos("P-001")),
                new Operacion("entrada", () -> recurso.registrarEntrada("P-001", cantidad)),
                new Operacion("salida", () -> recurso.registrarSalida("P-001", cantidad)),
                new Operacion("ajuste", () -> recurso.ajustarExistencias("P-001", cantidad))
        };
    }

    /** Hace que la operación indicada falle con la excepción dada. */
    private void fallarCon(String operacion, RuntimeException error) {
        switch (operacion) {
            case "listar" -> when(inventario.listarProductos()).thenThrow(error);
            case "buscar" -> when(consultas.buscarProducto(anyString())).thenThrow(error);
            case "movimientos" -> when(consultas.buscarMovimientos(anyString())).thenThrow(error);
            case "entrada" -> when(movimientos.registrarEntrada(anyString(), anyInt())).thenThrow(error);
            case "salida" -> when(movimientos.registrarSalida(anyString(), anyInt())).thenThrow(error);
            case "ajuste" -> when(inventario.actualizarExistencias(anyString(), anyInt())).thenThrow(error);
            default -> throw new IllegalArgumentException(operacion);
        }
    }

    private static Stream<String> todasLasOperaciones() {
        return Stream.of("listar", "buscar", "movimientos", "entrada", "salida", "ajuste");
    }

    private Response ejecutar(String nombre) {
        for (Operacion operacion : operaciones()) {
            if (operacion.nombre().equals(nombre)) {
                return operacion.ejecutar().get();
            }
        }
        throw new IllegalArgumentException(nombre);
    }

    /** Comprueba el estado y que el mensaje sea seguro; devuelve ese mensaje. */
    private String assertEstado(Response.Status esperado, Response respuesta) {
        try (respuesta) {
            assertEquals(esperado.getStatusCode(), respuesta.getStatus());
            assertInstanceOf(ErrorDto.class, respuesta.getEntity());
            String mensaje = ((ErrorDto) respuesta.getEntity()).mensaje();
            assertFalse(mensaje.isBlank());
            assertFalse(mensaje.contains(DETALLE_INTERNO));
            assertFalse(mensaje.contains("java."));
            assertFalse(mensaje.contains("Exception"));
            return mensaje;
        }
    }

    @ParameterizedTest(name = "{0}: Inventario no disponible responde 503")
    @MethodSource("todasLasOperaciones")
    @DisplayName("Servicio de inventario no disponible: HTTP 503")
    void servicioNoDisponibleResponde503(String operacion) {
        fallarCon(operacion, new InventarioNoDisponibleException(
                new IllegalStateException(DETALLE_INTERNO)));

        assertEstado(Response.Status.SERVICE_UNAVAILABLE, ejecutar(operacion));
    }

    @ParameterizedTest(name = "{0}: fallo inesperado responde 500")
    @MethodSource("todasLasOperaciones")
    @DisplayName("Error inesperado o inconsistencia interna: HTTP 500")
    void falloInesperadoResponde500(String operacion) {
        fallarCon(operacion, new IllegalStateException(DETALLE_INTERNO));

        assertEstado(Response.Status.INTERNAL_SERVER_ERROR, ejecutar(operacion));
    }

    @ParameterizedTest(name = "{0}: NullPointerException responde 500")
    @MethodSource("todasLasOperaciones")
    @DisplayName("Una NullPointerException interna no es un error del cliente")
    void nullPointerResponde500(String operacion) {
        fallarCon(operacion, new NullPointerException(DETALLE_INTERNO));

        assertEstado(Response.Status.INTERNAL_SERVER_ERROR, ejecutar(operacion));
    }

    @ParameterizedTest(name = "{0}: IllegalArgumentException en una consulta responde 500")
    @MethodSource("consultas")
    @DisplayName("Un IllegalArgumentException al consultar es una inconsistencia interna: HTTP 500")
    void illegalArgumentEnConsultaResponde500(String operacion) {
        // Por ejemplo, un tipo de movimiento desconocido guardado en la base.
        fallarCon(operacion, new IllegalArgumentException(DETALLE_INTERNO));

        assertEstado(Response.Status.INTERNAL_SERVER_ERROR, ejecutar(operacion));
    }

    private static Stream<String> consultas() {
        return Stream.of("listar", "buscar", "movimientos");
    }

    @Test
    @DisplayName("Salida que supera las existencias: HTTP 409")
    void salidaSinExistenciasResponde409() {
        fallarCon("salida", new IllegalArgumentException(DETALLE_INTERNO));

        assertEquals(
                "La salida supera las existencias disponibles del producto.",
                assertEstado(Response.Status.CONFLICT, ejecutar("salida"))
        );
    }

    @Test
    @DisplayName("Entrada que excede el máximo permitido: HTTP 409")
    void entradaQueExcedeElMaximoResponde409() {
        fallarCon("entrada", new IllegalArgumentException(DETALLE_INTERNO));

        assertEquals(
                "La entrada supera la cantidad máxima que admite el inventario.",
                assertEstado(Response.Status.CONFLICT, ejecutar("entrada"))
        );
    }

    @Test
    @DisplayName("Ajuste que el servicio rechaza como inválido: HTTP 400, no 409")
    void ajusteInvalidoResponde400() {
        fallarCon("ajuste", new IllegalArgumentException(DETALLE_INTERNO));

        assertEstado(Response.Status.BAD_REQUEST, ejecutar("ajuste"));
    }

    @ParameterizedTest(name = "{0}: producto inexistente responde 404")
    @MethodSource("operacionesSobreUnProducto")
    @DisplayName("Producto inexistente: HTTP 404")
    void productoInexistenteResponde404(String operacion) {
        when(inventario.actualizarExistencias(anyString(), anyInt())).thenReturn(false);
        when(movimientos.registrarEntrada(anyString(), anyInt())).thenReturn(false);
        when(movimientos.registrarSalida(anyString(), anyInt())).thenReturn(false);
        when(consultas.buscarProducto(anyString())).thenReturn(Optional.empty());
        when(consultas.buscarMovimientos(anyString())).thenReturn(Optional.empty());

        assertEstado(Response.Status.NOT_FOUND, ejecutar(operacion));
    }

    private static Stream<String> operacionesSobreUnProducto() {
        return Stream.of("buscar", "movimientos", "entrada", "salida", "ajuste");
    }

    @Test
    @DisplayName("Validaciones de entrada: HTTP 400 sin consultar Inventario")
    void validacionesResponden400() {
        CantidadRequest cero = new CantidadRequest(0);
        CantidadRequest negativa = new CantidadRequest(-2);

        assertEstado(Response.Status.BAD_REQUEST, recurso.registrarEntrada("P-001", cero));
        assertEstado(Response.Status.BAD_REQUEST, recurso.registrarSalida("P-001", cero));
        assertEstado(Response.Status.BAD_REQUEST, recurso.ajustarExistencias("P-001", negativa));
        assertEstado(Response.Status.BAD_REQUEST, recurso.registrarEntrada("P-001", new CantidadRequest(null)));
        assertEstado(Response.Status.BAD_REQUEST, recurso.registrarSalida("../x", new CantidadRequest(1)));
        assertEstado(Response.Status.BAD_REQUEST, recurso.buscarMovimientos("a b"));
    }
}
