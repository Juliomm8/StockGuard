import { Link } from 'react-router'
import { formatearFecha, formatearNumero, formatearVariacion } from '../../domain/formato'
import { variacionMovimiento } from '../../domain/operaciones'
import type { Movimiento } from '../../domain/tipos'
import { rutaDetalle } from '../../rutas'
import { InsigniaTipo } from '../comun/Insignias'

interface TablaMovimientosProps {
  movimientos: Movimiento[]
  /** Muestra la columna de producto (vista consolidada del panel general). */
  mostrarProducto?: boolean
  mostrarObservacion?: boolean
  titulo: string
}

export function TablaMovimientos({
  movimientos,
  mostrarProducto = false,
  mostrarObservacion = true,
  titulo,
}: TablaMovimientosProps) {
  return (
    <div className="tabla-contenedor">
      <table className="tabla">
        <caption className="solo-lectores">{titulo}</caption>
        <thead>
          <tr>
            <th scope="col">Fecha</th>
            {mostrarProducto && <th scope="col">Producto</th>}
            <th scope="col">Tipo</th>
            <th scope="col" className="numerica">
              Cantidad
            </th>
            <th scope="col" className="numerica">
              Existencia anterior
            </th>
            <th scope="col" className="numerica">
              Existencia posterior
            </th>
            {mostrarObservacion && <th scope="col">Observación</th>}
          </tr>
        </thead>
        <tbody>
          {movimientos.map((m) => {
            const variacion = variacionMovimiento(m.existenciaAnterior, m.existenciaPosterior)
            return (
              <tr key={m.id}>
                <td className="sin-salto">
                  <time dateTime={m.fechaMovimiento}>{formatearFecha(m.fechaMovimiento)}</time>
                </td>
                {mostrarProducto && (
                  <td>
                    <Link className="enlace-codigo" to={rutaDetalle(m.codigoProducto)}>
                      {m.codigoProducto}
                    </Link>
                  </td>
                )}
                <td>
                  <InsigniaTipo tipo={m.tipoMovimiento} />
                </td>
                <td
                  className={`numerica variacion ${variacion > 0 ? 'variacion--positiva' : variacion < 0 ? 'variacion--negativa' : ''}`}
                  title={`${formatearNumero(m.cantidadModificada)} unidades modificadas`}
                >
                  {formatearVariacion(variacion)}
                </td>
                <td className="numerica">{formatearNumero(m.existenciaAnterior)}</td>
                <td className="numerica fuerte">{formatearNumero(m.existenciaPosterior)}</td>
                {mostrarObservacion && (
                  <td className="texto-secundario">{m.observacion || 'Sin observación'}</td>
                )}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
