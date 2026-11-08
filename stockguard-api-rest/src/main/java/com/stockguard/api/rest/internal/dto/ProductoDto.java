package com.stockguard.api.rest.internal.dto;

import com.stockguard.inventario.api.Producto;

/** Representación HTTP de un producto. */
public record ProductoDto(
        String codigo,
        String nombre,
        int cantidadDisponible
) {

    public static ProductoDto desde(Producto producto) {
        return new ProductoDto(
                producto.codigo(),
                producto.nombre(),
                producto.cantidadDisponible()
        );
    }
}
