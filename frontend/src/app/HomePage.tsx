import { Link } from 'react-router-dom';
import { useAuth } from '../shared/auth/AuthContext';
import { ROLE_LABELS, isOperator, canBank } from '../shared/utils';

interface HomeCard { to: string; title: string; desc: string; icon: string; show: boolean }

export default function HomePage() {
  const { user } = useAuth();
  const operator = isOperator(user?.role);
  const banker = canBank(user?.role);

  const cards: HomeCard[] = [
    { to: '/bank', title: 'Банк', desc: 'Балансы, переводы, пополнения и списания.', icon: '🏦', show: banker },
    { to: '/accounts', title: 'Аккаунты', desc: 'Игроки и персонал: создание, роли, статусы.', icon: '🪪', show: operator },
    { to: '/cards', title: 'Карты', desc: 'Привязка NFC-карт к игрокам.', icon: '💳', show: banker },
    { to: '/terminals', title: 'Терминалы', desc: 'Банкоматы, кассы, ворота зон.', icon: '🖥️', show: banker },
    { to: '/audit', title: 'Аудит-лог', desc: 'Журнал всех событий системы.', icon: '🛡️', show: banker },
    { to: '/market', title: 'Маркетплейс', desc: 'Каталог товаров и услуг Найт-Сити.', icon: '🛒', show: true },
    { to: '/market/products', title: 'Товары', desc: 'Управление каталогом и остатками.', icon: '📦', show: banker },
    { to: '/news', title: 'Новости', desc: 'Анонсы и объявления мастеров.', icon: '📰', show: true },
    { to: '/me/balance', title: 'Мой счёт', desc: 'Ваш баланс и история операций.', icon: '👤', show: !banker },
  ];

  return (
    <div className="page" style={{ maxWidth: 1100 }}>
      <h1 className="page-title">Game Console</h1>
      <p className="subtext">
        {user ? `Добро пожаловать, ${user.publicName} (${ROLE_LABELS[user.role] || user.role}). ` : 'Добро пожаловать. '}
        Выберите раздел.
      </p>

      <div className="card-grid" style={{ marginTop: 24 }}>
        {cards.filter((c) => c.show).map((c) => (
          <Link key={c.to} to={c.to} className="home-card">
            <span className="home-card-ic">{c.icon}</span>
            <span className="home-card-title">{c.title}</span>
            <span className="home-card-desc">{c.desc}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
