const numero = new Intl.NumberFormat('es')

const fechaHora = new Intl.DateTimeFormat('es', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const hora = new Intl.DateTimeFormat('es', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})

export function formatearNumero(valor: number): string {
  return numero.format(valor)
}

export function formatearFecha(iso: string): string {
  const fecha = new Date(iso)
  return Number.isNaN(fecha.getTime()) ? iso : fechaHora.format(fecha)
}

export function formatearHora(fecha: Date): string {
  return hora.format(fecha)
}

/** Variación con signo explícito: +5, −2, 0. */
export function formatearVariacion(valor: number): string {
  if (valor > 0) return `+${formatearNumero(valor)}`
  if (valor < 0) return `−${formatearNumero(Math.abs(valor))}`
  return '0'
}

/** Cantidad seguida del sustantivo en singular o plural: «1 entrada», «3 entradas». */
export function contar(valor: number, singular: string, plural = `${singular}s`): string {
  return `${formatearNumero(valor)} ${valor === 1 ? singular : plural}`
}

export function unidades(valor: number): string {
  return contar(valor, 'unidad', 'unidades')
}
