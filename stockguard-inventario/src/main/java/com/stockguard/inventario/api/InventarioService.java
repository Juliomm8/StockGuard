package com.stockguard.inventario.api;

import java.util.List;

/**
 * Contrato público para consultar y administrar
 * las existencias del inventario de StockGuard.
 *
 * Los consumidores utilizan esta interfaz sin
 * conocer la implementación interna del servicio.
 */
public interface InventarioService {

    /**
     * Obtiene los productos registrados y sus
     * cantidades disponibles.
     *
     * @return lista de productos del inventario.
     */
    List<Producto> listarProductos();

    /**
     * Actualiza las existencias de un producto.
     *
     * @param codigo identificador del producto.
     * @param nuevaCantidad nueva cantidad disponible.
     * @return true si el producto fue actualizado;
     *         false si el código no existe.
     * @throws IllegalArgumentException si la cantidad
     *         es negativa.
     */
    boolean actualizarExistencias(
            String codigo,
            int nuevaCantidad
    );
}
