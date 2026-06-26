interface Props { message: string }
export default function SuccessMessage({ message }: Props) {
  return <div className="success-block">{message}</div>
}
