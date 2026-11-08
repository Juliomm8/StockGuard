package com.stockguard.inventario.internal;

import com.stockguard.inventario.api.Producto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Implementación JDBC transaccional del repositorio de inventario. */
final class JdbcInventarioRepository implements InventarioRepository {

    private static final String RUTA_ESQUEMA = "/db/schema.sql";
    private static final String SQL_INSERTAR_PRODUCTO = """
            INSERT INTO productos (codigo, nombre, cantidad_disponible)
            VALUES (?, ?, ?)
            ON CONFLICT (codigo) DO NOTHING
            """;
    private static final String SQL_LISTAR_PRODUCTOS = """
            SELECT codigo, nombre, cantidad_disponible
            FROM productos
            ORDER BY codigo
            """;
    private static final String SQL_BUSCAR_PRODUCTO_PARA_ACTUALIZAR = """
            SELECT nombre, cantidad_disponible
            FROM productos
            WHERE codigo = ?
            FOR UPDATE
            """;
    private static final String SQL_ACTUALIZAR_PRODUCTO = """
            UPDATE productos
            SET cantidad_disponible = ?, fecha_actualizacion = CURRENT_TIMESTAMP
            WHERE codigo = ?
            """;
    private static final String SQL_INSERTAR_MOVIMIENTO = """
            INSERT INTO movimientos_inventario (
                codigo_producto, tipo_movimiento, cantidad_modificada,
                existencia_anterior, existencia_posterior, observacion
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    JdbcInventarioRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(
                dataSource,
                "La fuente de datos es obligatoria"
        );
    }

    @Override
    public void inicializar(List<Producto> catalogoInicial) {
        Objects.requireNonNull(catalogoInicial, "El catálogo es obligatorio");

        enTransaccion(connection -> {
            ejecutarEsquema(connection);
            insertarCatalogoInicial(connection, catalogoInicial);
            return null;
        });
    }

    @Override
    public List<Producto> listarProductos() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement consulta = connection.prepareStatement(
                     SQL_LISTAR_PRODUCTOS
             );
             ResultSet resultados = consulta.executeQuery()) {
            List<Producto> productos = new ArrayList<>();
            while (resultados.next()) {
                productos.add(new Producto(
                        resultados.getString("codigo"),
                        resultados.getString("nombre"),
                        resultados.getInt("cantidad_disponible")
                ));
            }
            return List.copyOf(productos);
        } catch (SQLException error) {
            throw new InventarioPersistenceException(
                    "No fue posible consultar los productos en PostgreSQL.",
                    error
            );
        }
    }

    @Override
    public boolean actualizarExistencias(String codigo, int nuevaCantidad) {
        Objects.requireNonNull(codigo, "El código es obligatorio");
        if (nuevaCantidad < 0) {
            throw new IllegalArgumentException(
                    "La cantidad no puede ser negativa"
            );
        }

        return enTransaccion(connection -> {
            EstadoProducto actual = bloquearProducto(connection, codigo);
            if (actual == null) {
                return false;
            }

            if (actual.cantidadDisponible() == nuevaCantidad) {
                return true;
            }

            actualizarProducto(connection, codigo, nuevaCantidad);
            insertarMovimiento(
                    connection,
                    codigo,
                    "AJUSTE",
                    Math.abs(nuevaCantidad - actual.cantidadDisponible()),
                    actual.cantidadDisponible(),
                    nuevaCantidad,
                    "Ajuste de existencias mediante InventarioService"
            );
            return true;
        });
    }

    @Override
    public boolean registrarEntrada(String codigo, int cantidad) {
        validarMovimiento(codigo, cantidad);
        return enTransaccion(connection -> {
            EstadoProducto actual = bloquearProducto(connection, codigo);
            if (actual == null) {
                return false;
            }

            int nuevaCantidad;
            try {
                nuevaCantidad = Math.addExact(
                        actual.cantidadDisponible(),
                        cantidad
                );
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException(
                        "La cantidad excede el máximo permitido",
                        error
                );
            }

            actualizarProducto(connection, codigo, nuevaCantidad);
            insertarMovimiento(
                    connection,
                    codigo,
                    "ENTRADA",
                    cantidad,
                    actual.cantidadDisponible(),
                    nuevaCantidad,
                    "Entrada de inventario"
            );
            return true;
        });
    }

    @Override
    public boolean registrarSalida(String codigo, int cantidad) {
        validarMovimiento(codigo, cantidad);
        return enTransaccion(connection -> {
            EstadoProducto actual = bloquearProducto(connection, codigo);
            if (actual == null) {
                return false;
            }
            if (cantidad > actual.cantidadDisponible()) {
                throw new IllegalArgumentException(
                        "La salida supera las existencias disponibles"
                );
            }

            int nuevaCantidad = actual.cantidadDisponible() - cantidad;
            actualizarProducto(connection, codigo, nuevaCantidad);
            insertarMovimiento(
                    connection,
                    codigo,
                    "SALIDA",
                    cantidad,
                    actual.cantidadDisponible(),
                    nuevaCantidad,
                    "Salida de inventario"
            );
            return true;
        });
    }

    private void ejecutarEsquema(Connection connection) throws SQLException {
        try (InputStream recurso = getClass().getResourceAsStream(RUTA_ESQUEMA)) {
            if (recurso == null) {
                throw new SQLException(
                        "No se encontró el recurso " + RUTA_ESQUEMA
                );
            }
            String esquema = new String(
                    recurso.readAllBytes(),
                    StandardCharsets.UTF_8
            );
            for (String sentencia : esquema.split(";")) {
                String sql = sentencia.trim();
                if (!sql.isEmpty()) {
                    try (Statement instruccion = connection.createStatement()) {
                        instruccion.execute(sql);
                    }
                }
            }
        } catch (IOException error) {
            throw new SQLException(
                    "No fue posible leer el esquema SQL del bundle.",
                    error
            );
        }
    }

    private void insertarCatalogoInicial(
            Connection connection,
            List<Producto> catalogoInicial
    ) throws SQLException {
        try (PreparedStatement insercion = connection.prepareStatement(
                SQL_INSERTAR_PRODUCTO
        )) {
            for (Producto producto : catalogoInicial) {
                insercion.setString(1, producto.codigo());
                insercion.setString(2, producto.nombre());
                insercion.setInt(3, producto.cantidadDisponible());
                insercion.addBatch();
            }
            insercion.executeBatch();
        }
    }

    private EstadoProducto bloquearProducto(
            Connection connection,
            String codigo
    ) throws SQLException {
        try (PreparedStatement consulta = connection.prepareStatement(
                SQL_BUSCAR_PRODUCTO_PARA_ACTUALIZAR
        )) {
            consulta.setString(1, codigo);
            try (ResultSet resultado = consulta.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                return new EstadoProducto(
                        resultado.getString("nombre"),
                        resultado.getInt("cantidad_disponible")
                );
            }
        }
    }

    private void actualizarProducto(
            Connection connection,
            String codigo,
            int nuevaCantidad
    ) throws SQLException {
        try (PreparedStatement actualizacion = connection.prepareStatement(
                SQL_ACTUALIZAR_PRODUCTO
        )) {
            actualizacion.setInt(1, nuevaCantidad);
            actualizacion.setString(2, codigo);
            if (actualizacion.executeUpdate() != 1) {
                throw new SQLException(
                        "El producto dejó de existir durante la actualización"
                );
            }
        }
    }

    private void insertarMovimiento(
            Connection connection,
            String codigo,
            String tipo,
            int cantidad,
            int anterior,
            int posterior,
            String observacion
    ) throws SQLException {
        try (PreparedStatement insercion = connection.prepareStatement(
                SQL_INSERTAR_MOVIMIENTO
        )) {
            insercion.setString(1, codigo);
            insercion.setString(2, tipo);
            insercion.setInt(3, cantidad);
            insercion.setInt(4, anterior);
            insercion.setInt(5, posterior);
            insercion.setString(6, observacion);
            insercion.executeUpdate();
        }
    }

    private <T> T enTransaccion(OperacionSql<T> operacion) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T resultado = operacion.ejecutar(connection);
                connection.commit();
                return resultado;
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException errorRollback) {
                    error.addSuppressed(errorRollback);
                }
                throw error;
            }
        } catch (SQLException error) {
            throw new InventarioPersistenceException(
                    "No fue posible completar la transacción de inventario "
                    + "en PostgreSQL.",
                    error
            );
        }
    }

    private void validarMovimiento(String codigo, int cantidad) {
        Objects.requireNonNull(codigo, "El código es obligatorio");
        if (cantidad <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad del movimiento debe ser mayor que cero"
            );
        }
    }

    private record EstadoProducto(String nombre, int cantidadDisponible) {
    }

    @FunctionalInterface
    private interface OperacionSql<T> {
        T ejecutar(Connection connection) throws SQLException;
    }
}
