import type { ReactNode } from 'react'

export interface Indicador {
  etiqueta: string
  valor: ReactNode
  detalle?: ReactNode
  tono?: 'neutro' | 'alerta' | 'peligro'
  /** El valor es texto (por ejemplo, una fecha) y no una cifra. */
  textual?: boolean
}

/** Franja de cifras clave separadas por divisores. */
export function Indicadores({ elementos }: { elementos: Indicador[] }) {
  return (
    <dl className="indicadores">
      {elementos.map(({ etiqueta, valor, detalle, tono = 'neutro', textual = false }) => (
        <div key={etiqueta} className={`indicador indicador--${tono}`}>
          <dt className="indicador__etiqueta">{etiqueta}</dt>
          <dd className={`indicador__valor${textual ? ' indicador__valor--texto' : ''}`}>{valor}</dd>
          {detalle && <dd className="indicador__detalle">{detalle}</dd>}
        </div>
      ))}
    </dl>
  )
}
