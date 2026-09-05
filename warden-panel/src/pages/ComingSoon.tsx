interface ComingSoonProps {
  title: string
}

/** Honest placeholder for a nav item that exists in the spec but isn't built yet - never fakes data. */
export default function ComingSoon({ title }: ComingSoonProps) {
  return (
    <div>
      <h1>{title}</h1>
      <p className="muted">This page isn't available yet.</p>
    </div>
  )
}
