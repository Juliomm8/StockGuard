import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { InventarioProvider } from '../context/InventarioProvider'
import { NotificacionesProvider } from '../context/NotificacionesProvider'
import { OperacionesProvider } from '../context/OperacionesProvider'

/** Renderiza con los mismos proveedores que la aplicación. */
export function renderizar(contenido: ReactNode, { ruta = '/', patron = '*' } = {}) {
  const usuario = userEvent.setup()
  const resultado = render(
    <MemoryRouter initialEntries={[ruta]}>
      <NotificacionesProvider>
        <InventarioProvider>
          <OperacionesProvider>
            <Routes>
              <Route path={patron} element={contenido} />
            </Routes>
          </OperacionesProvider>
        </InventarioProvider>
      </NotificacionesProvider>
    </MemoryRouter>,
  )
  return { usuario, ...resultado }
}
