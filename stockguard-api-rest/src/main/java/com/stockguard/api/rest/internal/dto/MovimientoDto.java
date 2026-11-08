package com.stockguard.api.rest.internal.dto;

import com.stockguard.inventario.api.MovimientoInventario;

import java.time.format.DateTimeFormatter;

/** Representación HTTP de un movimiento de inventario. */
public record MovimientoDto(
        long id,
        String codigoProducto,
        String tipoMovimiento,
        int cantidadModificada,
        int existenciaAnterior,
        int existenciaPosterior,
        String fechaMovimiento,
        String observacion
) {

    public static MovimientoDto desde(MovimientoInventario movimiento) {
        return new MovimientoDto(
                movimiento.id(),
                movimiento.codigoProducto(),
                movimiento.tipo().name(),
                movimiento.cantidadModificada(),
                movimiento.existenciaAnterior(),
                movimiento.existenciaPosterior(),
                DateTimeFormatter.ISO_INSTANT.format(
                        movimiento.fechaMovimiento()
                ),
                movimiento.observacion()
        );
    }
}
