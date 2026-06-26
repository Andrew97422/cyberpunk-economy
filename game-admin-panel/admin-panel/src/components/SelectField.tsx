interface Option { value: string; label: string }

interface Props {
  id: string
  value: string
  onChange: (v: string) => void
  options: Option[]
  disabled?: boolean
}

export default function SelectField({ id, value, onChange, options, disabled }: Props) {
  return (
    <select
      id={id}
      value={value}
      onChange={e => onChange(e.target.value)}
      disabled={disabled}
    >
      {options.map(o => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}
