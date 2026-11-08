package com.stockguard.inventario.internal;

import com.stockguard.inventario.api.InventarioMovimientosService;
import com.stockguard.inventario.api.InventarioService;
import com.stockguard.inventario.api.Producto;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jdbc.DataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Implementación interna y persistente del servicio de inventario. */
@Component(
        name = "com.stockguard.inventario.internal.InventarioServiceImpl",
        configurationPid = "com.stockguard.inventario.internal.InventarioServiceImpl",
        service = {
                InventarioService.class,
                InventarioMovimientosService.class
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true
)
public class InventarioServiceImpl implements
        InventarioService,
        InventarioMovimientosService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(InventarioServiceImpl.class);

    private static final List<Producto> CATALOGO_INICIAL = List.of(
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

    @Reference(
            service = DataSourceFactory.class,
            target = "(osgi.jdbc.driver.class=org.postgresql.Driver)"
    )
    private DataSourceFactory dataSourceFactory;

    private volatile InventarioRepository repositorio;

    /** Constructor usado por Declarative Services. */
    public InventarioServiceImpl() {
    }

    /** Constructor de paquete para probar el servicio con un repositorio real. */
    InventarioServiceImpl(InventarioRepository repositorio) {
        this.repositorio = Objects.requireNonNull(
                repositorio,
                "El repositorio es obligatorio"
        );
    }

    /** Configura la conexión y verifica PostgreSQL antes de publicar servicios. */
    @Activate
    void activar(Map<String, Object> configuracion) throws SQLException {
        String url = propiedadObligatoria(configuracion, "jdbc.url");
        String usuario = propiedadObligatoria(configuracion, "jdbc.user");
        String contrasena = valor(configuracion, "jdbc.password");
        if (contrasena == null || contrasena.isBlank()) {
            contrasena = System.getenv("STOCKGUARD_DB_PASSWORD");
        }
        if (contrasena == null || contrasena.isBlank()) {
            throw new IllegalStateException(
                    "Configure jdbc.password en Config Admin o la variable "
                    + "de entorno STOCKGUARD_DB_PASSWORD."
            );
        }

        Properties propiedadesJdbc = new Properties();
        propiedadesJdbc.setProperty(DataSourceFactory.JDBC_URL, url);
        propiedadesJdbc.setProperty(DataSourceFactory.JDBC_USER, usuario);
        propiedadesJdbc.setProperty(DataSourceFactory.JDBC_PASSWORD, contrasena);

        try {
            InventarioRepository nuevoRepositorio =
                    new JdbcInventarioRepository(
                            dataSourceFactory.createDataSource(propiedadesJdbc)
                    );
            nuevoRepositorio.inicializar(CATALOGO_INICIAL);
            repositorio = nuevoRepositorio;
            LOGGER.info(
                    "Persistencia PostgreSQL de Inventario inicializada; "
                    + "el catálogo se conservó sin sobrescribir existencias."
            );
        } catch (SQLException | InventarioPersistenceException error) {
            LOGGER.error(
                    "No fue posible conectar con PostgreSQL o inicializar "
                    + "el inventario. El servicio no será publicado.",
                    error
            );
            throw error;
        }
    }

    @Deactivate
    void desactivar() {
        repositorio = null;
        LOGGER.info("Servicio persistente de Inventario detenido.");
    }

    @Override
    public List<Producto> listarProductos() {
        return repositorioActivo().listarProductos();
    }

    @Override
    public boolean actualizarExistencias(String codigo, int nuevaCantidad) {
        Objects.requireNonNull(codigo, "El código es obligatorio");
        if (nuevaCantidad < 0) {
            throw new IllegalArgumentException(
                    "La cantidad no puede ser negativa"
            );
        }
        return repositorioActivo().actualizarExistencias(codigo, nuevaCantidad);
    }

    @Override
    public boolean registrarEntrada(String codigo, int cantidad) {
        return repositorioActivo().registrarEntrada(codigo, cantidad);
    }

    @Override
    public boolean registrarSalida(String codigo, int cantidad) {
        return repositorioActivo().registrarSalida(codigo, cantidad);
    }

    private InventarioRepository repositorioActivo() {
        InventarioRepository actual = repositorio;
        if (actual == null) {
            throw new IllegalStateException(
                    "El servicio de Inventario no está activo."
            );
        }
        return actual;
    }

    private String propiedadObligatoria(
            Map<String, Object> configuracion,
            String nombre
    ) {
        String valor = valor(configuracion, nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "La propiedad de Config Admin " + nombre + " es obligatoria."
            );
        }
        return valor;
    }

    private String valor(Map<String, Object> configuracion, String nombre) {
        Object valor = configuracion.get(nombre);
        return valor == null ? null : valor.toString().trim();
    }
}
