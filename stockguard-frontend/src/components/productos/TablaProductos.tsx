import { ChevronDown, ChevronUp, ChevronsUpDown } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import type { CampoOrden, Direccion } from '../../domain/inventario'
import { rutaDetalle } from '../../rutas'
import { formatearNumero } from '../../domain/formato'
import type { Producto } from '../../domain/tipos'
import { InsigniaEstado } from '../comun/Insignias'
import { AccionesProducto } from '../movimientos/AccionesProducto'

interface Orden {
  campo: CampoOrden
  direccion: Direccion
  onOrdenar: (campo: CampoOrden) => void
}

interface TablaProductosProps {
  productos: Producto[]
  titulo: string
  orden?: Orden
  mostrarAcciones?: boolean
}

const COLUMNAS: { campo: CampoOrden; texto: string; numerica?: boolean }[] = [
  { campo: 'codigo', texto: 'Código' },
  { campo: 'nombre', texto: 'Nombre' },
  { campo: 'cantidadDisponible', texto: 'Disponible', numerica: true },
]

export function TablaProductos({ productos, titulo, orden, mostrarAcciones = true }: TablaProductosProps) {
  const navegar = useNavigate()
  const abrir = (codigo: string) => navegar(rutaDetalle(codigo))

  return (
    <div className="tabla-contenedor">
      <table className="tabla tabla--interactiva">
        <caption className="solo-lectores">{titulo}</caption>
        <thead>
          <tr>
            {COLUMNAS.map(({ campo, texto, numerica }) => (
              <th
                key={campo}
                scope="col"
                className={numerica ? 'numerica' : undefined}
                aria-sort={
                  orden?.campo === campo
                    ? orden.direccion === 'asc'
                      ? 'ascending'
                      : 'descending'
                    : undefined
                }
              >
                {orden ? (
                  <button type="button" className="orden" onClick={() => orden.onOrdenar(campo)}>
                    {texto}
                    <IconoOrden activo={orden.campo === campo} direccion={orden.direccion} />
                  </button>
                ) : (
                  texto
                )}
              </th>
            ))}
            <th scope="col">Estado</th>
            {mostrarAcciones && (
              <th scope="col" className="columna-acciones">
                <span className="solo-lectores">Acciones</span>
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {productos.map((p) => (
            <tr key={p.codigo} onClick={() => abrir(p.codigo)}>
              <td className="sin-salto">
                <Link
                  className="enlace-codigo"
                  to={rutaDetalle(p.codigo)}
                  onClick={(evento) => evento.stopPropagation()}
                >
                  {p.codigo}
                </Link>
              </td>
              <td>{p.nombre}</td>
              <td className="numerica fuerte">{formatearNumero(p.cantidadDisponible)}</td>
              <td>
                <InsigniaEstado cantidad={p.cantidadDisponible} />
              </td>
              {mostrarAcciones && (
                <td className="columna-acciones">
                  <AccionesProducto producto={p} compactas />
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function IconoOrden({ activo, direccion }: { activo: boolean; direccion: Direccion }) {
  if (!activo) return <ChevronsUpDown className="orden__icono orden__icono--inactivo" size={14} aria-hidden />
  const Icono = direccion === 'asc' ? ChevronUp : ChevronDown
  return <Icono className="orden__icono" size={14} aria-hidden />
}
