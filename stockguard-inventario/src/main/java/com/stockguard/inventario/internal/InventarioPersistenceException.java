package com.stockguard.inventario.internal;

/** Error de acceso a la persistencia PostgreSQL del inventario. */
final class InventarioPersistenceException extends RuntimeException {

    InventarioPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
