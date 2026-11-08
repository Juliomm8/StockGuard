import { ETIQUETA_TIPO } from '../../domain/operaciones'
import { ETIQUETA_ESTADO, estadoStock } from '../../domain/stock'
import type { TipoMovimiento } from '../../domain/tipos'

export function InsigniaEstado({ cantidad }: { cantidad: number }) {
  const estado = estadoStock(cantidad)
  return (
    <span className={`insignia insignia--${estado}`}>
      <span className="insignia__punto" aria-hidden />
      {ETIQUETA_ESTADO[estado]}
    </span>
  )
}

const CLASE_TIPO: Record<TipoMovimiento, string> = {
  ENTRADA: 'entrada',
  SALIDA: 'salida',
  AJUSTE: 'ajuste',
}

export function InsigniaTipo({ tipo }: { tipo: TipoMovimiento }) {
  return (
    <span className={`etiqueta-tipo etiqueta-tipo--${CLASE_TIPO[tipo] ?? 'ajuste'}`}>
      {ETIQUETA_TIPO[tipo] ?? tipo}
    </span>
  )
}
