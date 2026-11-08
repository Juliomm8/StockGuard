package com.stockguard.inventario.internal;

import com.stockguard.inventario.api.Producto;

import java.util.List;

/** Acceso persistente al catálogo y sus movimientos. */
interface InventarioRepository {

    void inicializar(List<Producto> catalogoInicial);

    List<Producto> listarProductos();

    boolean actualizarExistencias(String codigo, int nuevaCantidad);

    boolean registrarEntrada(String codigo, int cantidad);

    boolean registrarSalida(String codigo, int cantidad);
}
