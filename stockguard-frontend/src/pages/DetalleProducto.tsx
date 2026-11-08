import { ChevronLeft, History, PackageX } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { esNoEncontrado } from '../api/errores'
import { EncabezadoPagina } from '../components/comun/EncabezadoPagina'
import { AvisoError, Cargando, EstadoVacio } from '../components/comun/Estados'
import { Indicadores } from '../components/comun/Indicadores'
import { InsigniaEstado } from '../components/comun/Insignias'
import { Seccion } from '../components/comun/Seccion'
import { AccionesProducto } from '../components/movimientos/AccionesProducto'
import { TablaMovimientos } from '../components/movimientos/TablaMovimientos'
import { useInventario } from '../context/inventario'
import { formatearFecha, formatearNumero, unidades } from '../domain/formato'
import { ETIQUETA_TIPO } from '../domain/operaciones'
import { contarPorTipo, describirConteoTipos } from '../domain/inventario'
import { UMBRAL_STOCK, estadoStock } from '../domain/stock'
import type { Movimiento, TipoMovimiento } from '../domain/tipos'
import { useProductoDetalle } from '../hooks/useProductoDetalle'
import { RUTAS } from '../rutas'

type FiltroTipo = 'todos' | TipoMovimiento

const FILTROS_TIPO: { valor: FiltroTipo; texto: string }[] = [
  { valor: 'todos', texto: 'Todos' },
  { valor: 'ENTRADA', texto: 'Entradas' },
  { valor: 'SALIDA', texto: 'Salidas' },
  { valor: 'AJUSTE', texto: 'Ajustes' },
]

const volver = (
  <Link className="migas" to={RUTAS.inventario}>
    <ChevronLeft size={16} aria-hidden />
    Inventario
  </Link>
)

export function DetalleProducto() {
  const { codigo = '' } = useParams()
  const { recargar } = useInventario()
  const { datos, error, cargando } = useProductoDetalle(codigo)

  if (!datos) {
    if (esNoEncontrado(error)) {
      return (
        <>
          <EncabezadoPagina antes={volver} titulo="Producto no encontrado" />
          <EstadoVacio
            icono={PackageX}
            titulo={`No existe ningún producto con el código ${codigo}`}
            accion={
              <Link className="boton boton--secundario" to={RUTAS.inventario}>
                Volver al inventario
              </Link>
            }
          >
            Compruebe el código o búsquelo en el inventario.
          </EstadoVacio>
        </>
      )
    }
    return (
      <>
        <EncabezadoPagina antes={volver} titulo={codigo} />
        {error ? (
          <AvisoError error={error} onReintentar={recargar} />
        ) : (
          cargando && <Cargando texto="Consultando el producto…" />
        )}
      </>
    )
  }

  const { producto, movimientos } = datos
  const ultimo = movimientos[0]

  return (
    <>
      <EncabezadoPagina
        antes={volver}
        titulo={producto.nombre}
        descripcion={
          <>
            Código <span className="fuerte">{producto.codigo}</span>
          </>
        }
        acciones={<AccionesProducto producto={producto} />}
      />

      {error ? (
        <AvisoError error={error} onReintentar={recargar} titulo="No se pudo actualizar el producto" compacto />
      ) : null}

      <Indicadores
        elementos={[
          {
            etiqueta: 'Existencia actual',
            valor: formatearNumero(producto.cantidadDisponible),
            detalle: <InsigniaEstado cantidad={producto.cantidadDisponible} />,
            tono:
              estadoStock(producto.cantidadDisponible) === 'agotado'
                ? 'peligro'
                : estadoStock(producto.cantidadDisponible) === 'bajo'
                  ? 'alerta'
                  : 'neutro',
          },
          {
            etiqueta: 'Umbral de alerta',
            valor: `< ${UMBRAL_STOCK}`,
            detalle: 'Unidades, según el monitor de alertas',
          },
          {
            etiqueta: 'Movimientos registrados',
            valor: formatearNumero(movimientos.length),
            detalle: movimientos.length > 0 ? describirConteoTipos(movimientos) : undefined,
          },
          {
            etiqueta: 'Último movimiento',
            valor: ultimo ? formatearFecha(ultimo.fechaMovimiento) : 'Sin movimientos',
            textual: true,
            detalle: ultimo
              ? `${ETIQUETA_TIPO[ultimo.tipoMovimiento]} de ${unidades(ultimo.cantidadModificada)}`
              : undefined,
          },
        ]}
      />

      <HistorialMovimientos movimientos={movimientos} />
    </>
  )
}

function HistorialMovimientos({ movimientos }: { movimientos: Movimiento[] }) {
  const [filtro, setFiltro] = useState<FiltroTipo>('todos')
  const conteo = contarPorTipo(movimientos)
  const visibles = filtro === 'todos' ? movimientos : movimientos.filter((m) => m.tipoMovimiento === filtro)

  return (
    <Seccion
      titulo="Historial de movimientos"
      descripcion="Del más reciente al más antiguo."
      acciones={
        movimientos.length > 0 ? (
          <div className="segmentos" role="group" aria-label="Filtrar por tipo de movimiento">
            {FILTROS_TIPO.map(({ valor, texto }) => (
              <button
                key={valor}
                type="button"
                className="segmentos__opcion"
                aria-pressed={filtro === valor}
                onClick={() => setFiltro(valor)}
              >
                {texto}
                <span className="segmentos__contador">
                  {valor === 'todos' ? movimientos.length : conteo[valor]}
                </span>
              </button>
            ))}
          </div>
        ) : undefined
      }
    >
      {movimientos.length === 0 ? (
        <EstadoVacio icono={History} titulo="Este producto aún no registra movimientos">
          Las entradas, salidas y ajustes que se registren quedarán en este historial.
        </EstadoVacio>
      ) : visibles.length === 0 ? (
        <EstadoVacio icono={History} titulo="No hay movimientos de este tipo" />
      ) : (
        <TablaMovimientos movimientos={visibles} titulo="Historial de movimientos del producto" />
      )}
    </Seccion>
  )
}
