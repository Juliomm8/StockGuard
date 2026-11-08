package com.stockguard.inventario.internal;

import com.stockguard.inventario.api.MovimientoInventario;
import com.stockguard.inventario.api.Producto;

import java.util.List;
import java.util.Optional;

/** Acceso persistente al catálogo y sus movimientos. */
interface InventarioRepository {

    void inicializar(List<Producto> catalogoInicial);

    List<Producto> listarProductos();

    Optional<Producto> buscarProducto(String codigo);

    Optional<List<MovimientoInventario>> buscarMovimientos(String codigo);

    boolean actualizarExistencias(String codigo, int nuevaCantidad);

    boolean registrarEntrada(String codigo, int cantidad);

    boolean registrarSalida(String codigo, int cantidad);
}
