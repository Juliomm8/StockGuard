import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { listarProductos } from '../api/productos'
import { ApiError } from '../api/errores'
import { useRecurso } from '../hooks/useRecurso'
import { InventarioContext, type EstadoConexion, type ValorInventario } from './inventario'

function estadoConexion(cargando: boolean, error: unknown, hayDatos: boolean): EstadoConexion {
  if (error instanceof ApiError) {
    if (error.tipo === 'conexion') return 'sin-conexion'
    if (error.estado === 503) return 'servicio-no-disponible'
  }
  if (cargando && !hayDatos) return 'comprobando'
  return 'conectado'
}

/** Catálogo compartido por todas las pantallas; el backend es la fuente de verdad. */
export function InventarioProvider({ children }: { children: ReactNode }) {
  const [revision, setRevision] = useState(0)
  const { datos, cargando, error, actualizadoEn } = useRecurso('productos', revision, listarProductos)

  const recargar = useCallback(() => setRevision((actual) => actual + 1), [])

  const valor = useMemo<ValorInventario>(
    () => ({
      productos: datos,
      cargando,
      error,
      actualizadoEn,
      revision,
      conexion: estadoConexion(cargando, error, datos !== null),
      recargar,
    }),
    [datos, cargando, error, actualizadoEn, revision, recargar],
  )

  return <InventarioContext.Provider value={valor}>{children}</InventarioContext.Provider>
}
