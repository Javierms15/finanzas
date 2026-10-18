interface StatCardProps {
  etiqueta: string
  valor: string
  detalle?: string
  tono?: 'neutro' | 'positivo' | 'negativo'
}

const TONOS: Record<NonNullable<StatCardProps['tono']>, string> = {
  neutro: 'text-ink',
  positivo: 'text-up',
  negativo: 'text-down',
}

export function StatCard({ etiqueta, valor, detalle, tono = 'neutro' }: StatCardProps) {
  return (
    <div className="rounded-2xl bg-surface p-5">
      <div className="text-sm text-muted">{etiqueta}</div>
      <div className={`mt-2 text-2xl font-semibold tracking-tight ${TONOS[tono]}`}>{valor}</div>
      {detalle && <div className="mt-1 text-xs text-muted">{detalle}</div>}
    </div>
  )
}
