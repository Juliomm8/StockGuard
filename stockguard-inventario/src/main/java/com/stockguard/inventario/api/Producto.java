package com.stockguard.inventario.api;

/**
 * Representa la información pública de un producto
 * disponible en el inventario.
 *
 * @param codigo Identificador único del producto.
 * @param nombre Nombre descriptivo del producto.
 * @param cantidadDisponible Unidades disponibles en inventario.
 */
public record Producto(
        String codigo,
        String nombre,
        int cantidadDisponible
) {
}
