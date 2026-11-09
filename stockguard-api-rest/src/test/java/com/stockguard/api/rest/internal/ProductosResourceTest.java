package com.stockguard.api.rest.internal;

import com.stockguard.api.rest.internal.dto.CantidadRequest;
import com.stockguard.api.rest.internal.dto.ErrorDto;
import com.stockguard.api.rest.internal.dto.MovimientoDto;
import com.stockguard.api.rest.internal.dto.ProductoDto;
import com.stockguard.inventario.api.InventarioConsultaService;
import com.stockguard.inventario.api.InventarioMovimientosService;
import com.stockguard.inventario.api.InventarioNoDisponibleException;
import com.stockguard.inventario.api.InventarioService;
import com.stockguard.inventario.api.MovimientoInventario;
import com.stockguard.inventario.api.Producto;
import com.stockguard.inventario.api.TipoMovimiento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Recurso REST de productos")
class ProductosResourceTest {

    private static final Producto PRODUCTO =
            new Producto("P-001", "Teclado mecánico Logitech", 12);

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

    @Test
    @DisplayName("Lista productos mediante el contrato OSGi y responde con DTO")
    void listaProductosComoDto() {
        when(inventario.listarProductos()).thenReturn(List.of(PRODUCTO));

        try (Response respuesta = recurso.listarProductos()) {
            assertEquals(Response.Status.OK.getStatusCode(), respuesta.getStatus());
            assertEquals(
                    List.of(new ProductoDto("P-001", "Teclado mecánico Logitech", 12)),
                    respuesta.getEntity()
            );
        }
    }

    @Test
    @DisplayName("Busca un producto existente y responde 404 para un código ausente")
    void buscaProductoPorCodigo() {
        when(consultas.buscarProducto("P-001"))
                .thenReturn(Optional.of(PRODUCTO));
        when(consultas.buscarProducto("P-999"))
                .thenReturn(Optional.empty());

        try (Response encontrada = recurso.buscarProducto("P-001");
             Response ausente = recurso.buscarProducto("P-999")) {
            assertEquals(Response.Status.OK.getStatusCode(), encontrada.getStatus());
            assertEquals(
                    new ProductoDto("P-001", "Teclado mecánico Logitech", 12),
                    encontrada.getEntity()
            );
            assertEquals(
                    Response.Status.NOT_FOUND.getStatusCode(),
                    ausente.getStatus()
            );
        }
    }

    @Test
    @DisplayName("Acepta códigos de 32 caracteres, el máximo persistido")
    void aceptaCodigoConLongitudMaximaPersistida() {
        String codigo = "A".repeat(32);
        Producto producto = new Producto(codigo, "Producto de prueba", 1);
        when(consultas.buscarProducto(codigo)).thenReturn(Optional.of(producto));

        try (Response respuesta = recurso.buscarProducto(codigo)) {
            assertEquals(Response.Status.OK.getStatusCode(), respuesta.getStatus());
            assertEquals(
                    new ProductoDto(codigo, "Producto de prueba", 1),
                    respuesta.getEntity()
            );
        }

        verify(consultas).buscarProducto(codigo);
    }

    @Test
    @DisplayName("Rechaza códigos de 33 caracteres antes de consultar Inventario")
    void rechazaCodigoQueSuperaLongitudPersistida() {
        String codigo = "A".repeat(33);

        try (Response respuesta = recurso.buscarProducto(codigo)) {
            assertEquals(
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    respuesta.getStatus()
            );
        }

        verifyNoInteractions(inventario, movimientos, consultas);
    }

    @Test
    @DisplayName("Registra entradas y salidas con cantidades positivas")
    void registraEntradaYSalida() {
        when(movimientos.registrarEntrada("P-001", 4)).thenReturn(true);
        when(movimientos.registrarSalida("P-001", 2)).thenReturn(true);

        try (Response entrada = recurso.registrarEntrada(
                     "P-001", new CantidadRequest(4));
             Response salida = recurso.registrarSalida(
                     "P-001", new CantidadRequest(2))) {
            assertEquals(
                    Response.Status.NO_CONTENT.getStatusCode(),
                    entrada.getStatus()
            );
            assertEquals(
                    Response.Status.NO_CONTENT.getStatusCode(),
                    salida.getStatus()
            );
        }

        verify(movimientos).registrarEntrada("P-001", 4);
        verify(movimientos).registrarSalida("P-001", 2);
    }

    @Test
    @DisplayName("Rechaza códigos inválidos y cantidades no positivas")
    void validaCodigoYCantidad() {
        try (Response codigo = recurso.buscarProducto("../P-001");
             Response cero = recurso.registrarEntrada(
                     "P-001", new CantidadRequest(0));
             Response nula = recurso.registrarSalida("P-001", null)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), codigo.getStatus());
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), cero.getStatus());
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), nula.getStatus());
        }

        verifyNoInteractions(inventario, movimientos, consultas);
    }

    @Test
    @DisplayName("Permite ajuste a cero y rechaza valores negativos")
    void ajustaExistencias() {
        when(inventario.actualizarExistencias("P-001", 0)).thenReturn(true);

        try (Response ajustada = recurso.ajustarExistencias(
                     "P-001", new CantidadRequest(0));
             Response negativa = recurso.ajustarExistencias(
                     "P-001", new CantidadRequest(-1))) {
            assertEquals(
                    Response.Status.NO_CONTENT.getStatusCode(),
                    ajustada.getStatus()
            );
            assertEquals(
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    negativa.getStatus()
            );
        }

        verify(inventario).actualizarExistencias("P-001", 0);
    }

    @Test
    @DisplayName("Devuelve movimientos como DTO y distingue código inexistente")
    void consultaMovimientos() {
        MovimientoInventario movimiento = new MovimientoInventario(
                3L,
                "P-001",
                TipoMovimiento.ENTRADA,
                4,
                8,
                12,
                Instant.parse("2026-01-02T03:04:05Z"),
                "Reposición"
        );
        when(consultas.buscarMovimientos("P-001"))
                .thenReturn(Optional.of(List.of(movimiento)));
        when(consultas.buscarMovimientos("P-999"))
                .thenReturn(Optional.empty());

        try (Response encontrados = recurso.buscarMovimientos("P-001");
             Response ausentes = recurso.buscarMovimientos("P-999")) {
            assertEquals(
                    Response.Status.OK.getStatusCode(),
                    encontrados.getStatus()
            );
            assertEquals(
                    List.of(new MovimientoDto(
                            3L,
                            "P-001",
                            "ENTRADA",
                            4,
                            8,
                            12,
                            "2026-01-02T03:04:05Z",
                            "Reposición"
                    )),
                    encontrados.getEntity()
            );
            assertEquals(
                    Response.Status.NOT_FOUND.getStatusCode(),
                    ausentes.getStatus()
            );
        }
    }

    @Test
    @DisplayName("Responde 503 si Inventario no está vinculado o PostgreSQL falla")
    void servicioNoDisponible() {
        recurso.desvincularInventario(inventario);
        try (Response sinServicio = recurso.listarProductos()) {
            assertEquals(
                    Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                    sinServicio.getStatus()
            );
        }

        recurso.vincularInventario(inventario);
        when(inventario.listarProductos())
                .thenThrow(new InventarioNoDisponibleException(
                        new IllegalStateException("detalle interno")
                ));

        try (Response conFallo = recurso.listarProductos()) {
            assertEquals(
                    Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                    conFallo.getStatus()
            );
            assertFalse(conFallo.getEntity().toString().contains("detalle interno"));
        }
    }

    @Test
    @DisplayName("Mapea conflictos de negocio a 409 y fallos inesperados a 500")
    void mapeaErroresSinExponerDetalles() {
        when(movimientos.registrarSalida("P-001", 20))
                .thenThrow(new IllegalArgumentException("stock insuficiente"));
        when(inventario.listarProductos())
                .thenThrow(new IllegalStateException("detalle interno"));

        try (Response conflicto = recurso.registrarSalida(
                     "P-001", new CantidadRequest(20));
             Response interno = recurso.listarProductos()) {
            assertEquals(
                    Response.Status.CONFLICT.getStatusCode(),
                    conflicto.getStatus()
            );
            assertEquals(
                    Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    interno.getStatus()
            );
            assertFalse(interno.getEntity().toString().contains("detalle interno"));
            assertInstanceOf(ErrorDto.class, interno.getEntity());
        }
    }
}
