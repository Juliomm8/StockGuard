import { ArrowDownToLine, ArrowUpFromLine, CircleCheck, History } from 'lucide-react'
import { Link } from 'react-router'
import { EncabezadoPagina } from '../components/comun/EncabezadoPagina'
import { AvisoError, Cargando, EstadoVacio } from '../components/comun/Estados'
import { Indicadores, type Indicador } from '../components/comun/Indicadores'
import { Seccion } from '../components/comun/Seccion'
import { TablaMovimientos } from '../components/movimientos/TablaMovimientos'
import { TablaProductos } from '../components/productos/TablaProductos'
import { useInventario } from '../context/inventario'
import { useOperaciones } from '../context/operaciones'
import { formatearNumero } from '../domain/formato'
import { describirConteoTipos, productosQueRequierenAtencion, resumirInventario } from '../domain/inventario'
import { UMBRAL_STOCK } from '../domain/stock'
import type { Producto } from '../domain/tipos'
import { useMovimientosRecientes, type MovimientosConsolidados } from '../hooks/useMovimientosRecientes'
import type { Recurso } from '../hooks/useRecurso'
import { RUTAS } from '../rutas'

const MOVIMIENTOS_VISIBLES = 8

export function PanelGeneral() {
  const { productos, error, recargar } = useInventario()
  const { abrirOperacion } = useOperaciones()
  const historial = useMovimientosRecientes()

  const acciones = (
    <>
      <button
        type="button"
        className="boton boton--primario"
        onClick={() => abrirOperacion('entrada')}
        disabled={!productos}
      >
        <ArrowDownToLine size={16} aria-hidden />
        Registrar entrada
      </button>
      <button
        type="button"
        className="boton boton--secundario"
        onClick={() => abrirOperacion('salida')}
        disabled={!productos}
      >
        <ArrowUpFromLine size={16} aria-hidden />
        Registrar salida
      </button>
    </>
  )

  return (
    <>
      <EncabezadoPagina
        titulo="Panel general"
        descripcion="Existencias y movimientos registrados en StockGuard."
        acciones={acciones}
      />

      {!productos && error ? (
        <AvisoError error={error} onReintentar={recargar} />
      ) : !productos ? (
        <Cargando texto="Consultando el inventario…" />
      ) : (
        <>
          {error ? (
            <AvisoError
              error={error}
              onReintentar={recargar}
              titulo="No se pudo actualizar el inventario"
              compacto
            />
          ) : null}
          <Indicadores elementos={indicadores(productos, historial)} />

          <div className="panel-rejilla">
            <Seccion
              titulo="Productos que requieren atención"
              descripcion={`Existencia menor que ${UMBRAL_STOCK} unidades, el umbral del monitor de alertas.`}
              acciones={
                <Link className="enlace" to={RUTAS.alertas}>
                  Ver alertas
                </Link>
              }
            >
              <ProductosEnAtencion productos={productos} />
            </Seccion>

            <Seccion
              titulo="Últimos movimientos"
              descripcion="Entradas, salidas y ajustes de todos los productos."
              acciones={
                <Link className="enlace" to={RUTAS.inventario}>
                  Ver inventario
                </Link>
              }
            >
              <UltimosMovimientos historial={historial} />
            </Seccion>
          </div>
        </>
      )}
    </>
  )
}

function indicadores(
  productos: Producto[],
  historial: Recurso<MovimientosConsolidados>,
): Indicador[] {
  const resumen = resumirInventario(productos)
  const datos = historial.datos
  const historialFallido = !datos || todasLasConsultasFallaron(datos)

  return [
    { etiqueta: 'Productos registrados', valor: formatearNumero(resumen.totalProductos) },
    { etiqueta: 'Unidades disponibles', valor: formatearNumero(resumen.totalUnidades) },
    {
      etiqueta: 'Bajo el umbral de alerta',
      valor: formatearNumero(resumen.bajoUmbral),
      detalle:
        resumen.agotados > 0
          ? `${formatearNumero(resumen.agotados)} sin existencias`
          : `Menos de ${UMBRAL_STOCK} unidades`,
      tono: resumen.agotados > 0 ? 'peligro' : resumen.bajoUmbral > 0 ? 'alerta' : 'neutro',
    },
    {
      etiqueta: 'Movimientos registrados',
      valor:
        !datos && historial.cargando
          ? '…'
          : historialFallido
            ? 'No disponible'
            : formatearNumero(datos.movimientos.length),
      textual: Boolean(datos) && historialFallido,
      detalle: !historialFallido ? describirConteoTipos(datos.movimientos) : undefined,
    },
  ]
}

function ProductosEnAtencion({ productos }: { productos: Producto[] }) {
  const enAtencion = productosQueRequierenAtencion(productos)
  if (enAtencion.length === 0) {
    return (
      <EstadoVacio icono={CircleCheck} titulo="Ningún producto está bajo el umbral">
        Todos los productos tienen al menos {UMBRAL_STOCK} unidades disponibles.
      </EstadoVacio>
    )
  }
  return <TablaProductos productos={enAtencion} titulo="Productos que requieren atención" />
}

function todasLasConsultasFallaron(datos: MovimientosConsolidados): boolean {
  return datos.productosConsultados > 0 && datos.productosFallidos === datos.productosConsultados
}

function UltimosMovimientos({ historial }: { historial: Recurso<MovimientosConsolidados> }) {
  const { recargar } = useInventario()
  const { datos, cargando, error } = historial
  if (!datos) {
    if (error) return <AvisoError error={error} onReintentar={recargar} compacto />
    return cargando ? <Cargando texto="Consultando movimientos…" /> : null
  }
  if (todasLasConsultasFallaron(datos)) {
    return (
      <AvisoError
        titulo="No se pudo consultar el historial"
        mensaje="Ninguna consulta de movimientos obtuvo respuesta. Compruebe la conexión con el servidor."
        onReintentar={recargar}
        compacto
      />
    )
  }
  return (
    <>
      {datos.productosFallidos > 0 && (
        <p className="nota nota--advertencia">
          No se pudo consultar el historial de {datos.productosFallidos} de {datos.productosConsultados} productos.
        </p>
      )}
      {datos.movimientos.length === 0 ? (
        <EstadoVacio icono={History} titulo="Aún no hay movimientos">
          Las entradas, salidas y ajustes aparecerán aquí en cuanto se registren.
        </EstadoVacio>
      ) : (
        <TablaMovimientos
          movimientos={datos.movimientos.slice(0, MOVIMIENTOS_VISIBLES)}
          mostrarProducto
          mostrarObservacion={false}
          titulo="Últimos movimientos registrados"
        />
      )}
    </>
  )
}
