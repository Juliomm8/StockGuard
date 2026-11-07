package com.stockguard.inventario.internal;

import com.stockguard.inventario.api.InventarioService;
import com.stockguard.inventario.api.Producto;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación interna del servicio de inventario.
 *
 * Administra productos en memoria y publica el contrato
 * InventarioService mediante OSGi Declarative Services.
 */
@Component(
        service = InventarioService.class,
        immediate = true
)
public class InventarioServiceImpl implements InventarioService {

    private final Map<String, Producto> productos =
            new ConcurrentHashMap<>();

    /**
     * Inicializa los productos de demostración
     * cuando OSGi activa el componente.
     */
    @Activate
    void activar() {
        productos.clear();

        registrar(new Producto("P-001", "Teclado mecánico Logitech", 12));
        registrar(new Producto("P-002", "Mouse inalámbrico Logitech", 3));
        registrar(new Producto("P-003", "Monitor Samsung 24 pulgadas", 8));
        registrar(new Producto("P-004", "Cable HDMI 2 metros", 1));
        registrar(new Producto("P-005", "Memoria RAM Kingston 16 GB", 20));
        registrar(new Producto("P-006", "SSD Kingston 1 TB", 14));
        registrar(new Producto("P-007", "Audífonos HyperX Cloud", 4));
        registrar(new Producto("P-008", "Webcam Logitech C920", 7));
        registrar(new Producto("P-009", "Adaptador USB-C", 2));
        registrar(new Producto("P-010", "Hub USB 3.0", 18));
        registrar(new Producto("P-011", "Disco duro externo 2 TB", 6));
        registrar(new Producto("P-012", "Router TP-Link Wi-Fi 6", 9));
        registrar(new Producto("P-013", "Fuente de poder 650 W", 11));
        registrar(new Producto("P-014", "Pasta térmica Arctic MX-4", 25));
        registrar(new Producto("P-015", "Soporte para laptop", 5));
    }

    /**
     * Devuelve una lista ordenada de productos.
     */
    @Override
    public List<Producto> listarProductos() {
        return productos.values()
                .stream()
                .sorted(Comparator.comparing(Producto::codigo))
                .toList();
    }

    /**
     * Actualiza la cantidad disponible de un producto.
     */
    @Override
    public boolean actualizarExistencias(
            String codigo,
            int nuevaCantidad
    ) {
        Objects.requireNonNull(codigo, "El código es obligatorio");

        if (nuevaCantidad < 0) {
            throw new IllegalArgumentException(
                    "La cantidad no puede ser negativa"
            );
        }

        return productos.computeIfPresent(
                codigo,
                (clave, actual) -> new Producto(
                        actual.codigo(),
                        actual.nombre(),
                        nuevaCantidad
                )
        ) != null;
    }

    /**
     * Registra un producto en el inventario.
     */
    private void registrar(Producto producto) {
        productos.put(producto.codigo(), producto);
    }
}
