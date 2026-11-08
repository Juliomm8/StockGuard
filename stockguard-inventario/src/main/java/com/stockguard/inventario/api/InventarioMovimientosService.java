package com.stockguard.inventario.api;

/**
 * Operaciones de entrada y salida que conservan el historial del inventario.
 *
 * Este contrato complementa {@link InventarioService} sin modificar su API.
 */
public interface InventarioMovimientosService {

    /**
     * Registra una entrada de unidades para un producto existente.
     *
     * @param codigo identificador del producto.
     * @param cantidad unidades que ingresan; debe ser mayor que cero.
     * @return {@code true} si se registró el movimiento, o {@code false}
     *         cuando el producto no existe.
     * @throws IllegalArgumentException si la cantidad es inválida.
     */
    boolean registrarEntrada(String codigo, int cantidad);

    /**
     * Registra una salida de unidades para un producto existente.
     *
     * @param codigo identificador del producto.
     * @param cantidad unidades que salen; debe ser mayor que cero.
     * @return {@code true} si se registró el movimiento, o {@code false}
     *         cuando el producto no existe.
     * @throws IllegalArgumentException si la cantidad es inválida o supera
     *         las existencias disponibles.
     */
    boolean registrarSalida(String codigo, int cantidad);
}
