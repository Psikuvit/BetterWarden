import type { DailyCount } from '../api/dashboard'

interface ActivityChartProps {
  data: DailyCount[]
}

// Plain inline SVG - no charting library. Spec explicitly asked to avoid a heavy SPA framework
// for the panel in general (overridden by using React at all, per explicit request), but pulling
// in a whole charting dependency for one bar chart still isn't worth it.
export default function ActivityChart({ data }: ActivityChartProps) {
  if (data.length === 0) {
    return <p className="muted">No data.</p>
  }
  const max = Math.max(1, ...data.map((d) => d.count))
  const width = 600
  const height = 120
  const barGap = 2
  const barWidth = width / data.length - barGap

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="activity-chart" role="img" aria-label="Punishment activity, last 30 days">
      {data.map((d, i) => {
        const barHeight = (d.count / max) * (height - 4)
        return (
          <rect
            key={d.date}
            x={i * (barWidth + barGap)}
            y={height - barHeight}
            width={barWidth}
            height={barHeight}
            rx={1}
            fill="var(--accent)"
          >
            <title>
              {d.date}: {d.count}
            </title>
          </rect>
        )
      })}
    </svg>
  )
}
