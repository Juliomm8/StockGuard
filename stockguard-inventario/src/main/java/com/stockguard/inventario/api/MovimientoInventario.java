package com.stockguard.inventario.api;

import java.time.Instant;

/** Registro inmutable de un cambio persistido de existencias. */
public record MovimientoInventario(
        long id,
        String codigoProducto,
        TipoMovimiento tipo,
        int cantidadModificada,
        int existenciaAnterior,
        int existenciaPosterior,
        Instant fechaMovimiento,
        String observacion
) {
}
