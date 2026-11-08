package com.stockguard.inventario.internal;

import com.stockguard.inventario.api.InventarioNoDisponibleException;
import com.stockguard.inventario.api.MovimientoInventario;
import com.stockguard.inventario.api.Producto;
import com.stockguard.inventario.api.TipoMovimiento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@DisplayName("Servicio persistente de inventario PostgreSQL")
class InventarioServiceImplTest {

    private static final List<Producto> CATALOGO_ESPERADO = List.of(
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

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("stockguard_test")
                    .withUsername("stockguard_test")
                    .withPassword("stockguard_test");

    private DataSource dataSource;
    private JdbcInventarioRepository repositorio;
    private InventarioServiceImpl inventario;

    @BeforeEach
    void prepararInventarioAislado() throws SQLException {
        PGSimpleDataSource fuente = new PGSimpleDataSource();
        fuente.setURL(POSTGRES.getJdbcUrl());
        fuente.setUser(POSTGRES.getUsername());
        fuente.setPassword(POSTGRES.getPassword());
        dataSource = fuente;
        repositorio = new JdbcInventarioRepository(dataSource);

        // La primera inicialización crea el esquema; las siguientes son idempotentes.
        repositorio.inicializar(CATALOGO_ESPERADO);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "DROP TRIGGER IF EXISTS rechazar_movimiento_prueba "
                    + "ON movimientos_inventario"
            );
            statement.execute(
                    "DROP FUNCTION IF EXISTS rechazar_movimiento_prueba()"
            );
            statement.executeUpdate("DELETE FROM movimientos_inventario");
            statement.executeUpdate("DELETE FROM productos");
        }
        repositorio.inicializar(CATALOGO_ESPERADO);
        inventario = new InventarioServiceImpl(repositorio);
    }

    @Test
    @DisplayName("Conecta con una instancia real de PostgreSQL")
    void conectaConPostgreSqlReal() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2));
            assertEquals("stockguard_test", connection.getCatalog());
        }
    }

    @Test
    @DisplayName("Carga el catálogo inicial completo, único y ordenado")
    void cargaCatalogoInicialCompletoEnOrden() {
        List<Producto> productos = inventario.listarProductos();

        assertEquals(15, productos.size());
        assertEquals(CATALOGO_ESPERADO, productos);
        assertEquals(
                productos.size(),
                productos.stream().map(Producto::codigo).distinct().count()
        );
        assertTrue(productos.stream()
                .allMatch(producto -> producto.cantidadDisponible() >= 0));
    }

    @Test
    @DisplayName("Busca productos por código sin convertir ausencias en errores")
    void buscaProductoExistenteYDesconocido() {
        assertEquals(
                new Producto("P-001", "Teclado mecánico Logitech", 12),
                inventario.buscarProducto("P-001").orElseThrow()
        );
        assertTrue(inventario.buscarProducto("P-999").isEmpty());
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
        assertEquals(5, producto("P-015").cantidadDisponible());
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
    @DisplayName("Comunica una indisponibilidad si el componente ya fue detenido")
    void informaServicioNoDisponibleTrasDesactivacion() {
        inventario.desactivar();

        InventarioNoDisponibleException error = assertThrows(
                InventarioNoDisponibleException.class,
                () -> inventario.listarProductos()
        );

        assertEquals(
                "El servicio de Inventario no está disponible.",
                error.getMessage()
        );
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
                productos.stream().sorted(Comparator.comparing(Producto::codigo))
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
    @DisplayName("Permite lecturas y ajustes concurrentes consistentes")
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
                tarea.get(120, TimeUnit.SECONDS);
            }
        } finally {
            inicio.countDown();
            ejecutor.shutdownNow();
            assertTrue(ejecutor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("La inicialización repetida conserva cambios y no duplica productos")
    void inicializacionRepetidaConservaExistenciasModificadas() {
        assertTrue(inventario.actualizarExistencias("P-001", 7));

        repositorio.inicializar(CATALOGO_ESPERADO);

        assertEquals(15, inventario.listarProductos().size());
        assertEquals(7, producto("P-001").cantidadDisponible());
    }

    @Test
    @DisplayName("Registra entradas, salidas y ajustes con sus existencias")
    void registraMovimientosDeInventario() throws SQLException {
        assertTrue(inventario.registrarEntrada("P-001", 3));
        assertTrue(inventario.registrarSalida("P-001", 2));
        assertTrue(inventario.actualizarExistencias("P-001", 20));

        assertEquals(20, producto("P-001").cantidadDisponible());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement("""
                     SELECT tipo_movimiento, cantidad_modificada,
                            existencia_anterior, existencia_posterior,
                            fecha_movimiento
                     FROM movimientos_inventario
                     WHERE codigo_producto = ?
                     ORDER BY id
                     """)) {
            query.setString(1, "P-001");
            try (ResultSet resultados = query.executeQuery()) {
                assertTrue(resultados.next());
                assertEquals("ENTRADA", resultados.getString("tipo_movimiento"));
                assertEquals(3, resultados.getInt("cantidad_modificada"));
                assertEquals(12, resultados.getInt("existencia_anterior"));
                assertEquals(15, resultados.getInt("existencia_posterior"));
                assertNotNull(resultados.getTimestamp("fecha_movimiento"));

                assertTrue(resultados.next());
                assertEquals("SALIDA", resultados.getString("tipo_movimiento"));
                assertEquals(2, resultados.getInt("cantidad_modificada"));
                assertEquals(15, resultados.getInt("existencia_anterior"));
                assertEquals(13, resultados.getInt("existencia_posterior"));

                assertTrue(resultados.next());
                assertEquals("AJUSTE", resultados.getString("tipo_movimiento"));
                assertEquals(7, resultados.getInt("cantidad_modificada"));
                assertEquals(13, resultados.getInt("existencia_anterior"));
                assertEquals(20, resultados.getInt("existencia_posterior"));
                assertFalse(resultados.next());
            }
        }
    }

    @Test
    @DisplayName("Consulta movimientos y diferencia productos sin historial")
    void consultaHistorialDeMovimientos() {
        assertEquals(List.of(), inventario.buscarMovimientos("P-001").orElseThrow());
        assertTrue(inventario.buscarMovimientos("P-999").isEmpty());

        assertTrue(inventario.registrarEntrada("P-001", 2));
        assertTrue(inventario.registrarSalida("P-001", 1));

        List<MovimientoInventario> historial = inventario
                .buscarMovimientos("P-001")
                .orElseThrow();
        assertEquals(2, historial.size());
        var movimientoReciente = inventario.buscarMovimientos("P-001")
                .orElseThrow()
                .get(0);
        assertEquals(TipoMovimiento.SALIDA, movimientoReciente.tipo());
        assertEquals(1, movimientoReciente.cantidadModificada());
        assertEquals(14, movimientoReciente.existenciaAnterior());
        assertEquals(13, movimientoReciente.existenciaPosterior());
    }

    @Test
    @DisplayName("Rechaza movimientos inválidos y evita existencias negativas")
    void rechazaMovimientosInvalidos() {
        assertThrows(
                IllegalArgumentException.class,
                () -> inventario.registrarEntrada("P-001", 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> inventario.registrarSalida("P-001", -1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> inventario.registrarSalida("P-002", 4)
        );

        assertEquals(12, producto("P-001").cantidadDisponible());
        assertEquals(3, producto("P-002").cantidadDisponible());
    }

    @Test
    @DisplayName("Devuelve false para entradas y salidas de productos inexistentes")
    void movimientosDeProductoInexistenteDevuelvenFalse() {
        assertFalse(inventario.registrarEntrada("P-999", 1));
        assertFalse(inventario.registrarSalida("P-999", 1));
    }

    @Test
    @DisplayName("Revierte la actualización si falla el registro del movimiento")
    void revierteTransaccionSiFallaElMovimiento() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION rechazar_movimiento_prueba()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        RAISE EXCEPTION 'fallo de prueba';
                    END;
                    $$
                    """);
            statement.execute("""
                    CREATE TRIGGER rechazar_movimiento_prueba
                    BEFORE INSERT ON movimientos_inventario
                    FOR EACH ROW EXECUTE FUNCTION rechazar_movimiento_prueba()
                    """);
        }

        assertThrows(
                InventarioNoDisponibleException.class,
                () -> inventario.registrarEntrada("P-001", 2)
        );

        assertEquals(12, producto("P-001").cantidadDisponible());
        assertEquals(0, cantidadMovimientos("P-001"));
    }

    @Test
    @DisplayName("Conserva productos y existencias al recrear el componente")
    void conservaExistenciasTrasReinicioDelComponente() {
        assertTrue(inventario.actualizarExistencias("P-001", 8));

        JdbcInventarioRepository repositorioReiniciado =
                new JdbcInventarioRepository(dataSource);
        repositorioReiniciado.inicializar(CATALOGO_ESPERADO);
        InventarioServiceImpl componenteReiniciado =
                new InventarioServiceImpl(repositorioReiniciado);

        assertEquals(15, componenteReiniciado.listarProductos().size());
        assertEquals(
                new Producto("P-001", "Teclado mecánico Logitech", 8),
                componenteReiniciado.listarProductos().get(0)
        );
    }

    @Test
    @DisplayName("Serializa entradas concurrentes sin perder actualizaciones")
    void entradasConcurrentesSeAplicanUnaSolaVez() throws Exception {
        int operaciones = 40;
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> tareas = new ArrayList<>();

        try {
            for (int indice = 0; indice < operaciones; indice++) {
                tareas.add(ejecutor.submit(() -> {
                    inicio.await();
                    return inventario.registrarEntrada("P-001", 1);
                }));
            }
            inicio.countDown();
            for (Future<Boolean> tarea : tareas) {
                assertTrue(tarea.get(30, TimeUnit.SECONDS));
            }
        } finally {
            inicio.countDown();
            ejecutor.shutdownNow();
            assertTrue(ejecutor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(12 + operaciones, producto("P-001").cantidadDisponible());
        assertEquals(operaciones, cantidadMovimientos("P-001"));
    }

    @Test
    @DisplayName("PostgreSQL rechaza cantidades negativas a nivel de esquema")
    void esquemaRechazaExistenciasNegativas() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE productos SET cantidad_disponible = -1 "
                     + "WHERE codigo = ?"
             )) {
            update.setString(1, "P-001");
            assertThrows(SQLException.class, update::executeUpdate);
        }
        assertEquals(12, producto("P-001").cantidadDisponible());
    }

    private Producto producto(String codigo) {
        return inventario.listarProductos().stream()
                .filter(producto -> producto.codigo().equals(codigo))
                .findFirst()
                .orElseThrow();
    }

    private int cantidadMovimientos(String codigo) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT COUNT(*) FROM movimientos_inventario "
                     + "WHERE codigo_producto = ?"
             )) {
            query.setString(1, codigo);
            try (ResultSet resultados = query.executeQuery()) {
                assertTrue(resultados.next());
                return resultados.getInt(1);
            }
        }
    }
}
