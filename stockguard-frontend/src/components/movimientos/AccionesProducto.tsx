import { ArrowDownToLine, ArrowUpFromLine, Scale } from 'lucide-react'
import { useOperaciones } from '../../context/operaciones'
import type { Producto } from '../../domain/tipos'

interface AccionesProductoProps {
  producto: Producto
  /** Botones solo con icono, para filas de tabla. */
  compactas?: boolean
}

export function AccionesProducto({ producto, compactas = false }: AccionesProductoProps) {
  const { abrirOperacion } = useOperaciones()
  const sinExistencias = producto.cantidadDisponible <= 0

  const acciones = [
    { operacion: 'entrada', texto: 'Entrada', icono: ArrowDownToLine, deshabilitada: false },
    { operacion: 'salida', texto: 'Salida', icono: ArrowUpFromLine, deshabilitada: sinExistencias },
    { operacion: 'ajuste', texto: 'Ajuste', icono: Scale, deshabilitada: false },
  ] as const

  return (
    <div className={compactas ? 'acciones-fila' : 'grupo-botones'}>
      {acciones.map(({ operacion, texto, icono: Icono, deshabilitada }) => {
        const descripcion = `${texto} de ${producto.codigo}`
        return (
          <button
            key={operacion}
            type="button"
            className={compactas ? 'boton-icono boton-icono--borde' : 'boton boton--secundario'}
            onClick={(evento) => {
              evento.stopPropagation()
              abrirOperacion(operacion, producto.codigo)
            }}
            disabled={deshabilitada}
            aria-label={compactas ? descripcion : undefined}
            title={deshabilitada ? 'Sin existencias disponibles para una salida' : compactas ? descripcion : undefined}
          >
            <Icono size={16} aria-hidden />
            {!compactas && texto}
          </button>
        )
      })}
    </div>
  )
}
