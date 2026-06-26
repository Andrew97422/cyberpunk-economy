interface Props { title?: string; children: React.ReactNode }
export default function PageSection({ title, children }: Props) {
  return (
    <section className="page-section">
      {title && <h2 className="section-title">{title}</h2>}
      {children}
    </section>
  )
}
