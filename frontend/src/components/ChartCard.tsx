import type { ReactNode } from 'react'

interface ChartCardProps {
  titulo: string
  subtitulo?: string
  children: ReactNode
  className?: string
}

export function ChartCard({ titulo, subtitulo, children, className }: ChartCardProps) {
  return (
    <section className={`rounded-2xl bg-surface p-5 sm:p-6 ${className ?? ''}`}>
      <div className="mb-4">
        <h3 className="text-base font-semibold text-ink">{titulo}</h3>
        {subtitulo && <p className="mt-0.5 text-xs text-muted">{subtitulo}</p>}
      </div>
      {children}
    </section>
  )
}
