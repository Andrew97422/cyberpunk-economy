import { Link } from 'react-router-dom';
import { useAuth } from '../../../shared/auth/AuthContext';
import { ROLE_LABELS } from '../../../shared/utils';

const ACTIONS = [
  { to: '/bank/deposit', title: 'Пополнить игрока', desc: 'Зачислить деньги выбранному игроку.' },
  { to: '/bank/withdraw', title: 'Списать у игрока', desc: 'Снять деньги с баланса игрока.' },
  { to: '/bank/transfer', title: 'Перевод между игроками', desc: 'Переместить деньги от одного игрока другому.' },
  { to: '/bank/balance', title: 'Баланс', desc: 'Посмотреть баланс конкретного игрока.' },
  { to: '/bank/history', title: 'История операций', desc: 'Список последних операций игрока.' },
  { to: '/bank/reverse', title: 'Отменить операцию', desc: 'Откатить уже выполненную операцию по её ID.' },
];

export default function BankingHomePage() {
  const { user } = useAuth();
  return (
    <div className="page">
      <h1 className="page-title">Банк</h1>
      <p className="subtext">
        {user?.publicName
          ? `Вы вошли как ${user.publicName} (${ROLE_LABELS[user.role] || user.role}).`
          : 'Панель банковских операций.'}
      </p>

      <div className="info-cards" style={{ marginTop: 24 }}>
        {ACTIONS.map((a) => (
          <Link key={a.to} to={a.to} className="info-card">
            <h3>{a.title}</h3>
            <p>{a.desc}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
