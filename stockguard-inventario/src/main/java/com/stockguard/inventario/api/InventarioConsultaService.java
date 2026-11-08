package com.stockguard.inventario.api;

import java.util.List;
import java.util.Optional;

/** Consultas complementarias del inventario y su historial de movimientos. */
public interface InventarioConsultaService {

    /**
     * Busca un producto por su código.
     *
     * @param codigo código del producto.
     * @return producto si existe.
     * @throws InventarioNoDisponibleException si Inventario no está disponible.
     */
    Optional<Producto> buscarProducto(String codigo);

    /**
     * Consulta los movimientos de un producto, del más reciente al más antiguo.
     *
     * @param codigo código del producto.
     * @return vacío si el producto no existe; lista vacía si existe y aún no
     *         registra movimientos.
     * @throws InventarioNoDisponibleException si Inventario no está disponible.
     */
    Optional<List<MovimientoInventario>> buscarMovimientos(String codigo);
}
