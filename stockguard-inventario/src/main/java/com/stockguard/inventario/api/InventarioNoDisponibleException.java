package com.stockguard.inventario.api;

/** Indica que el servicio no pudo completar una operación de inventario. */
public final class InventarioNoDisponibleException extends RuntimeException {

    public InventarioNoDisponibleException(Throwable cause) {
        super("El servicio de Inventario no está disponible.", cause);
    }
}
