interface StatCardProps {
  etiqueta: string
  valor: string
  detalle?: string
  tono?: 'neutro' | 'positivo' | 'negativo'
}

const TONOS: Record<NonNullable<StatCardProps['tono']>, string> = {
  neutro: 'text-slate-900',
  positivo: 'text-emerald-600',
  negativo: 'text-rose-600',
}

export function StatCard({ etiqueta, valor, detalle, tono = 'neutro' }: StatCardProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-sm font-medium text-slate-500">{etiqueta}</div>
      <div className={`mt-1 text-2xl font-semibold ${TONOS[tono]}`}>{valor}</div>
      {detalle && <div className="mt-1 text-xs text-slate-400">{detalle}</div>}
    </div>
  )
}
