interface Props { message: string }
export default function ErrorBlock({ message }: Props) {
  return <div className="error-block">{message}</div>
}
