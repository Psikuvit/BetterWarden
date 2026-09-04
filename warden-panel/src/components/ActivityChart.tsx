import type { DailyCount } from '../api/dashboard'

interface ActivityChartProps {
  data: DailyCount[]
}

// Plain inline SVG - no charting library. Spec explicitly asked to avoid a heavy SPA framework
// for the panel in general (overridden by using React at all, per explicit request), but pulling
// in a whole charting dependency for one bar chart still isn't worth it.
export default function ActivityChart({ data }: ActivityChartProps) {
  // The 30-day range always has all days present (zero-filled), so data.length === 0 never
  // actually happens - checking the total instead. Found by actually looking at the rendered
  // chart with a fresh empty database: every bar at 0 height renders as nothing at all, not an
  // empty chart with a visible baseline, so a real "no data" message reads much better here.
  const total = data.reduce((sum, d) => sum + d.count, 0)
  if (data.length === 0 || total === 0) {
    return <p className="muted">No punishments issued in the last 30 days.</p>
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
