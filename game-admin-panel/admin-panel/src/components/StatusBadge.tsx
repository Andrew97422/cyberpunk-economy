import { STATUS_LABELS } from '../utils/helpers'

interface Props { status: string }

export default function StatusBadge({ status }: Props) {
  const cls = `badge badge-${status.toLowerCase()}`
  return <span className={cls}>{STATUS_LABELS[status] || status}</span>
}
