interface ComingSoonProps {
  title: string
}

/** Honest placeholder for a nav item that exists in the spec but isn't built yet - never fakes data. */
export default function ComingSoon({ title }: ComingSoonProps) {
  return (
    <div>
      <h1>{title}</h1>
      <p className="muted">Not built yet - see docs/spec/04-PANEL.txt and PLAN.md Stage 5.</p>
    </div>
  )
}
