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

        registrar(new Producto("P-001", "Teclado", 12));
        registrar(new Producto("P-002", "Mouse", 3));
        registrar(new Producto("P-003", "Monitor", 8));
        registrar(new Producto("P-004", "Cable HDMI", 1));
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
