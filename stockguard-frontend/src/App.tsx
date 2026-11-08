import { Route, Routes } from 'react-router'
import { InventarioProvider } from './context/InventarioProvider'
import { NotificacionesProvider } from './context/NotificacionesProvider'
import { OperacionesProvider } from './context/OperacionesProvider'
import { AppShell } from './layout/AppShell'
import { Alertas } from './pages/Alertas'
import { DetalleProducto } from './pages/DetalleProducto'
import { Inventario } from './pages/Inventario'
import { NoEncontrada } from './pages/NoEncontrada'
import { PanelGeneral } from './pages/PanelGeneral'

export function App() {
  return (
    <NotificacionesProvider>
      <InventarioProvider>
        <OperacionesProvider>
          <Routes>
            <Route element={<AppShell />}>
              <Route index element={<PanelGeneral />} />
              <Route path="inventario" element={<Inventario />} />
              <Route path="inventario/:codigo" element={<DetalleProducto />} />
              <Route path="alertas" element={<Alertas />} />
              <Route path="*" element={<NoEncontrada />} />
            </Route>
          </Routes>
        </OperacionesProvider>
      </InventarioProvider>
    </NotificacionesProvider>
  )
}
