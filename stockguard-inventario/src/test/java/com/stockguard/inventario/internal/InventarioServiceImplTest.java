package com.stockguard.inventario.internal;

import com.stockguard.inventario.api.Producto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Servicio de inventario")
class InventarioServiceImplTest {

    private InventarioServiceImpl inventario;

    @BeforeEach
    void inicializarInventario() {
        inventario = new InventarioServiceImpl();
        inventario.activar();
    }

    @Test
    @DisplayName("Carga los 15 productos iniciales con sus datos y orden")
    void cargaCatalogoInicialCompletoEnOrden() {
        List<Producto> productos = inventario.listarProductos();
        List<Producto> esperados = List.of(
                new Producto("P-001", "Teclado mecánico Logitech", 12),
                new Producto("P-002", "Mouse inalámbrico Logitech", 3),
                new Producto("P-003", "Monitor Samsung 24 pulgadas", 8),
                new Producto("P-004", "Cable HDMI 2 metros", 1),
                new Producto("P-005", "Memoria RAM Kingston 16 GB", 20),
                new Producto("P-006", "SSD Kingston 1 TB", 14),
                new Producto("P-007", "Audífonos HyperX Cloud", 4),
                new Producto("P-008", "Webcam Logitech C920", 7),
                new Producto("P-009", "Adaptador USB-C", 2),
                new Producto("P-010", "Hub USB 3.0", 18),
                new Producto("P-011", "Disco duro externo 2 TB", 6),
                new Producto("P-012", "Router TP-Link Wi-Fi 6", 9),
                new Producto("P-013", "Fuente de poder 650 W", 11),
                new Producto("P-014", "Pasta térmica Arctic MX-4", 25),
                new Producto("P-015", "Soporte para laptop", 5)
        );

        assertEquals(15, productos.size());
        assertEquals(esperados, productos);
        assertEquals(
                productos.size(),
                productos.stream().map(Producto::codigo).distinct().count()
        );
        assertTrue(productos.stream()
                .allMatch(producto -> producto.cantidadDisponible() >= 0));
    }

    @Test
    @DisplayName("Clasifica correctamente las existencias iniciales")
    void clasificaStockNormalYBajo() {
        List<Producto> productos = inventario.listarProductos();

        assertEquals(
                11,
                productos.stream()
                        .filter(producto -> producto.cantidadDisponible() >= 5)
                        .count()
        );
        assertEquals(
                4,
                productos.stream()
                        .filter(producto -> producto.cantidadDisponible() < 5)
                        .count()
        );
        assertEquals(
                5,
                productos.stream()
                        .filter(producto -> producto.codigo().equals("P-015"))
                        .findFirst()
                        .orElseThrow()
                        .cantidadDisponible()
        );
    }

    @Test
    @DisplayName("Actualiza un producto existente sin cambiar su identidad")
    void actualizaExistenciasYConservaCodigoNombre() {
        assertTrue(inventario.actualizarExistencias("P-001", 17));

        assertEquals(
                new Producto("P-001", "Teclado mecánico Logitech", 17),
                producto("P-001")
        );
    }

    @Test
    @DisplayName("Permite actualizar a cero y a exactamente cinco unidades")
    void permiteStockCeroYUmbralExacto() {
        assertTrue(inventario.actualizarExistencias("P-002", 0));
        assertEquals(0, producto("P-002").cantidadDisponible());

        assertTrue(inventario.actualizarExistencias("P-002", 5));
        assertEquals(5, producto("P-002").cantidadDisponible());
    }

    @Test
    @DisplayName("Devuelve false si se actualiza un código inexistente")
    void devuelveFalseParaCodigoInexistente() {
        assertFalse(inventario.actualizarExistencias("P-999", 10));
        assertEquals(15, inventario.listarProductos().size());
    }

    @Test
    @DisplayName("Rechaza cantidades negativas sin modificar el producto")
    void rechazaCantidadNegativa() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> inventario.actualizarExistencias("P-001", -1)
        );

        assertEquals("La cantidad no puede ser negativa", error.getMessage());
        assertEquals(12, producto("P-001").cantidadDisponible());
    }

    @Test
    @DisplayName("Rechaza códigos nulos con el mensaje definido por el servicio")
    void rechazaCodigoNulo() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> inventario.actualizarExistencias(null, 1)
        );

        assertEquals("El código es obligatorio", error.getMessage());
    }

    @Test
    @DisplayName("Mantiene el catálogo consistente después de varias actualizaciones")
    void mantieneCatalogoTrasVariasActualizaciones() {
        assertTrue(inventario.actualizarExistencias("P-001", 0));
        assertTrue(inventario.actualizarExistencias("P-015", 5));
        assertTrue(inventario.actualizarExistencias("P-001", 19));

        List<Producto> productos = inventario.listarProductos();
        assertEquals(15, productos.size());
        assertEquals(
                productos.size(),
                productos.stream().map(Producto::codigo).distinct().count()
        );
        assertEquals(
                productos.stream()
                        .sorted(Comparator.comparing(Producto::codigo))
                        .toList(),
                productos
        );
        assertEquals(
                new Producto("P-001", "Teclado mecánico Logitech", 19),
                producto("P-001")
        );
        assertEquals(
                new Producto("P-015", "Soporte para laptop", 5),
                producto("P-015")
        );
    }

    @Test
    @DisplayName("Permite lecturas y actualizaciones concurrentes consistentes")
    void permiteLecturasYActualizacionesConcurrentes() throws Exception {
        int iteraciones = 400;
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(6);
        List<Future<Void>> tareas = new ArrayList<>();

        try {
            for (int escritor = 0; escritor < 4; escritor++) {
                int indiceEscritor = escritor;
                tareas.add(ejecutor.submit((Callable<Void>) () -> {
                    inicio.await();
                    for (int iteracion = 0; iteracion < iteraciones; iteracion++) {
                        int cantidad = (indiceEscritor + iteracion) % 101;
                        assertTrue(inventario.actualizarExistencias(
                                "P-001",
                                cantidad
                        ));
                    }
                    return null;
                }));
            }

            for (int lector = 0; lector < 2; lector++) {
                tareas.add(ejecutor.submit((Callable<Void>) () -> {
                    inicio.await();
                    for (int iteracion = 0; iteracion < iteraciones; iteracion++) {
                        List<Producto> productos = inventario.listarProductos();
                        assertEquals(15, productos.size());
                        assertTrue(productos.stream().allMatch(
                                producto -> producto.cantidadDisponible() >= 0
                        ));
                        assertEquals(
                                productos.stream()
                                        .sorted(Comparator.comparing(Producto::codigo))
                                        .toList(),
                                productos
                        );
                    }
                    return null;
                }));
            }

            inicio.countDown();
            for (Future<Void> tarea : tareas) {
                tarea.get(20, TimeUnit.SECONDS);
            }
        } finally {
            inicio.countDown();
            ejecutor.shutdownNow();
            assertTrue(ejecutor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Producto producto(String codigo) {
        return inventario.listarProductos().stream()
                .filter(producto -> producto.codigo().equals(codigo))
                .findFirst()
                .orElseThrow();
    }
}
