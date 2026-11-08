CREATE TABLE IF NOT EXISTS productos (
    codigo VARCHAR(32) PRIMARY KEY CHECK (BTRIM(codigo) <> ''),
    nombre VARCHAR(200) NOT NULL CHECK (BTRIM(nombre) <> ''),
    cantidad_disponible INTEGER NOT NULL DEFAULT 0
        CHECK (cantidad_disponible >= 0),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS movimientos_inventario (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo_producto VARCHAR(32) NOT NULL
        REFERENCES productos(codigo) ON UPDATE CASCADE ON DELETE RESTRICT,
    tipo_movimiento VARCHAR(10) NOT NULL
        CHECK (tipo_movimiento IN ('ENTRADA', 'SALIDA', 'AJUSTE')),
    cantidad_modificada INTEGER NOT NULL CHECK (cantidad_modificada > 0),
    existencia_anterior INTEGER NOT NULL CHECK (existencia_anterior >= 0),
    existencia_posterior INTEGER NOT NULL CHECK (existencia_posterior >= 0),
    fecha_movimiento TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observacion VARCHAR(500),
    CHECK (
        (tipo_movimiento = 'ENTRADA'
            AND existencia_posterior::BIGINT
                = existencia_anterior::BIGINT + cantidad_modificada::BIGINT)
        OR
        (tipo_movimiento = 'SALIDA'
            AND existencia_anterior::BIGINT
                = existencia_posterior::BIGINT + cantidad_modificada::BIGINT)
        OR
        (tipo_movimiento = 'AJUSTE'
            AND ABS(existencia_posterior::BIGINT
                - existencia_anterior::BIGINT)
                = cantidad_modificada::BIGINT)
    )
);

CREATE INDEX IF NOT EXISTS idx_movimientos_inventario_codigo_fecha
    ON movimientos_inventario (codigo_producto, fecha_movimiento DESC);
