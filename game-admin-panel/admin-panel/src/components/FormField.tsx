interface Props {
  label: string
  id: string
  required?: boolean
  children: React.ReactNode
}
export default function FormField({ label, id, required, children }: Props) {
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}{required && <span className="required">*</span>}</label>
      {children}
    </div>
  )
}
