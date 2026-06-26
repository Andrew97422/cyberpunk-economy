export default function LoadingBlock({ text = 'Загрузка...' }: { text?: string }) {
  return <div className="loading-block">{text}</div>
}
