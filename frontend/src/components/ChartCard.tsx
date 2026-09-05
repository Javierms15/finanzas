import type { ReactNode } from 'react'

interface ChartCardProps {
  titulo: string
  subtitulo?: string
  children: ReactNode
  className?: string
}

export function ChartCard({ titulo, subtitulo, children, className }: ChartCardProps) {
  return (
    <div className={`rounded-xl border border-slate-200 bg-white p-5 shadow-sm ${className ?? ''}`}>
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-slate-700">{titulo}</h3>
        {subtitulo && <p className="text-xs text-slate-400">{subtitulo}</p>}
      </div>
      {children}
    </div>
  )
}
