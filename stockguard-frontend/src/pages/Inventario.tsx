import { PackageSearch, Search, X } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { EncabezadoPagina } from '../components/comun/EncabezadoPagina'
import { AvisoError, Cargando, EstadoVacio } from '../components/comun/Estados'
import { TablaProductos } from '../components/productos/TablaProductos'
import { useInventario } from '../context/inventario'
import { formatearNumero } from '../domain/formato'
import {
  contarPorEstado,
  filtrarYOrdenar,
  type CampoOrden,
  type CriteriosInventario,
  type Direccion,
  type FiltroEstado,
} from '../domain/inventario'
import { UMBRAL_STOCK } from '../domain/stock'

const FILTROS: { valor: FiltroEstado; texto: string }[] = [
  { valor: 'todos', texto: 'Todos' },
  { valor: 'disponible', texto: 'Disponibles' },
  { valor: 'bajo', texto: 'Stock bajo' },
  { valor: 'agotado', texto: 'Agotados' },
]

const CAMPOS_ORDEN: CampoOrden[] = ['codigo', 'nombre', 'cantidadDisponible']

function leerCriterios(parametros: URLSearchParams): CriteriosInventario {
  const estado = FILTROS.find((f) => f.valor === parametros.get('estado'))?.valor
  const orden = CAMPOS_ORDEN.find((campo) => campo === parametros.get('orden'))
  return {
    texto: parametros.get('q') ?? '',
    estado: estado ?? 'todos',
    orden: orden ?? 'codigo',
    direccion: parametros.get('dir') === 'desc' ? 'desc' : 'asc',
  }
}

export function Inventario() {
  const { productos, error, recargar } = useInventario()
  const [parametros, setParametros] = useSearchParams()
  const criterios = leerCriterios(parametros)

  function actualizar(cambios: Partial<CriteriosInventario>) {
    const siguiente = { ...criterios, ...cambios }
    const nuevos = new URLSearchParams()
    if (siguiente.texto) nuevos.set('q', siguiente.texto)
    if (siguiente.estado !== 'todos') nuevos.set('estado', siguiente.estado)
    if (siguiente.orden !== 'codigo') nuevos.set('orden', siguiente.orden)
    if (siguiente.direccion !== 'asc') nuevos.set('dir', siguiente.direccion)
    setParametros(nuevos, { replace: true })
  }

  function ordenarPor(campo: CampoOrden) {
    const direccion: Direccion =
      criterios.orden === campo && criterios.direccion === 'asc' ? 'desc' : 'asc'
    actualizar({ orden: campo, direccion })
  }

  const visibles = productos ? filtrarYOrdenar(productos, criterios) : []
  const conteo = productos ? contarPorEstado(productos) : null
  const hayFiltros = criterios.texto !== '' || criterios.estado !== 'todos'

  return (
    <>
      <EncabezadoPagina
        titulo="Inventario"
        descripcion={`Catálogo de productos con su existencia actual. Se considera stock bajo una existencia menor que ${UMBRAL_STOCK} unidades.`}
      />

      {!productos && error ? (
        <AvisoError error={error} onReintentar={recargar} />
      ) : !productos || !conteo ? (
        <Cargando texto="Consultando el inventario…" />
      ) : (
        <section className="seccion" aria-label="Productos">
          {error ? (
            <AvisoError error={error} onReintentar={recargar} titulo="No se pudo actualizar el inventario" compacto />
          ) : null}
          <div className="barra-herramientas">
            <div className="buscador">
              <Search className="buscador__icono" size={16} aria-hidden />
              <input
                type="search"
                className="campo__control buscador__control"
                placeholder="Buscar por código o nombre"
                aria-label="Buscar productos por código o nombre"
                value={criterios.texto}
                onChange={(evento) => actualizar({ texto: evento.target.value })}
              />
              {criterios.texto && (
                <button
                  type="button"
                  className="boton-icono buscador__limpiar"
                  onClick={() => actualizar({ texto: '' })}
                  aria-label="Borrar búsqueda"
                >
                  <X size={14} aria-hidden />
                </button>
              )}
            </div>

            <div className="segmentos" role="group" aria-label="Filtrar por estado">
              {FILTROS.map(({ valor, texto }) => (
                <button
                  key={valor}
                  type="button"
                  className="segmentos__opcion"
                  aria-pressed={criterios.estado === valor}
                  onClick={() => actualizar({ estado: valor })}
                >
                  {texto}
                  <span className="segmentos__contador">{conteo[valor]}</span>
                </button>
              ))}
            </div>
          </div>

          <p className="resultado-conteo" aria-live="polite">
            {visibles.length === productos.length
              ? `${formatearNumero(productos.length)} productos`
              : `${formatearNumero(visibles.length)} de ${formatearNumero(productos.length)} productos`}
          </p>

          {visibles.length === 0 ? (
            <EstadoVacio
              icono={PackageSearch}
              titulo={productos.length === 0 ? 'El inventario no tiene productos' : 'Ningún producto coincide con la búsqueda'}
              accion={
                hayFiltros ? (
                  <button
                    type="button"
                    className="boton boton--secundario"
                    onClick={() => actualizar({ texto: '', estado: 'todos' })}
                  >
                    Quitar filtros
                  </button>
                ) : undefined
              }
            >
              {hayFiltros ? 'Pruebe con otro código, nombre o estado.' : null}
            </EstadoVacio>
          ) : (
            <TablaProductos
              productos={visibles}
              titulo="Productos del inventario"
              orden={{ campo: criterios.orden, direccion: criterios.direccion, onOrdenar: ordenarPor }}
            />
          )}
        </section>
      )}
    </>
  )
}
