interface Props {
  message: string
  onConfirm: () => void
  onCancel: () => void
}
export default function ConfirmDialog({ message, onConfirm, onCancel }: Props) {
  return (
    <div className="overlay">
      <div className="dialog">
        <p>{message}</p>
        <div className="dialog-actions">
          <button className="btn btn-danger" onClick={onConfirm}>Подтвердить</button>
          <button className="btn btn-secondary" onClick={onCancel}>Отмена</button>
        </div>
      </div>
    </div>
  )
}
