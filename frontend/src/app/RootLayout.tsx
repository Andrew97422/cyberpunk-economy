import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../shared/auth/AuthContext';
import SessionTimer from '../shared/auth/SessionTimer';
import { ROLE_LABELS, isOperator, canBank } from '../shared/utils';
import { loadAccounts } from '../shared/api/accountsCache';

function navClass({ isActive }: { isActive: boolean }) {
  return isActive ? 'nav-link active' : 'nav-link';
}

interface NavItem { to: string; label: string; end?: boolean; banker?: boolean }
interface NavGroup { id: string; title: string; icon: string; show: boolean; items: NavItem[] }

export default function RootLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  const operator = isOperator(user?.role);
  const banker = canBank(user?.role);

  // Warm the front-end accounts cache once for operators (powers instant player search).
  useEffect(() => {
    if (operator) loadAccounts();
  }, [operator]);

  // Collapsible groups (persisted).
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>(() => {
    try { return JSON.parse(localStorage.getItem('navCollapsed') || '{}'); } catch { return {}; }
  });
  const toggleGroup = (id: string) =>
    setCollapsed((c) => {
      const next = { ...c, [id]: !c[id] };
      localStorage.setItem('navCollapsed', JSON.stringify(next));
      return next;
    });

  const groups: NavGroup[] = [
    {
      id: 'bank', title: 'Банк', icon: '🏦', show: banker, items: [
        { to: '/bank', label: 'Обзор', end: true },
        { to: '/bank/balance', label: 'Баланс' },
        { to: '/bank/history', label: 'История операций' },
        { to: '/bank/deposit', label: 'Пополнить игрока' },
        { to: '/bank/withdraw', label: 'Списать у игрока' },
        { to: '/bank/transfer', label: 'Перевод' },
        { to: '/bank/reverse', label: 'Отменить операцию' },
        { to: '/bank/event', label: 'Экономическое событие' },
        { to: '/crypto', label: 'Криптобиржа' },
      ],
    },
    {
      id: 'manage', title: 'Управление', icon: '🛠', show: operator, items: [
        { to: '/dashboard', label: 'Главная' },
        { to: '/accounts', label: 'Аккаунты', end: true },
        { to: '/pins', label: 'PIN-коды' },
        { to: '/cards', label: 'Карты', banker: true },
        { to: '/terminals', label: 'Терминалы', banker: true },
        { to: '/terminals/pos', label: 'Касса (считыватель)', banker: true },
        { to: '/analytics', label: 'Аналитика', banker: true },
        { to: '/audit', label: 'Аудит-лог', banker: true },
      ],
    },
    {
      id: 'market', title: 'Маркетплейс', icon: '🛒', show: true, items: [
        { to: '/market', label: 'Магазин', end: true },
        { to: '/market/orders', label: 'Мои покупки' },
        { to: '/market/inventory', label: 'Мой инвентарь' },
        { to: '/market/products', label: 'Товары', banker: true },
        { to: '/market/scenarios', label: 'Сценарии цен', banker: true },
        { to: '/market/manage-orders', label: 'Заказы', banker: true },
      ],
    },
    {
      id: 'news', title: 'Новости', icon: '📰', show: true, items: [
        { to: '/news', label: 'Лента', end: true },
        { to: '/news/manage', label: 'Управление', banker: true },
      ],
    },
    {
      id: 'me', title: 'Мой счёт', icon: '👤', show: !banker, items: [
        { to: '/me/balance', label: 'Мой баланс' },
        { to: '/me/history', label: 'Мои операции' },
        { to: '/me/pay', label: 'Оплатить / Перевод' },
        { to: '/crypto', label: 'Криптобиржа' },
      ],
    },
  ];

  const closeMenu = () => setMenuOpen(false);
  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="layout">
      <header className="topbar">
        <button className="hamburger" onClick={() => setMenuOpen(true)} aria-label="Открыть меню">☰</button>
        <span className="accent-dot" />
        <span className="topbar-title">Game Console</span>
      </header>

      {menuOpen && <div className="drawer-overlay" onClick={closeMenu} />}

      <aside className={menuOpen ? 'sidebar open' : 'sidebar'}>
        <Link to="/" className="sidebar-logo" onClick={closeMenu} title="На главную">
          <span className="accent-dot" />
          <span>Game Console</span>
        </Link>

        {/* NavLink clicks bubble up and close the mobile drawer; group toggles stopPropagation. */}
        <nav className="sidebar-nav" onClick={closeMenu}>
          {groups.filter((g) => g.show).map((g) => {
            const items = g.items.filter((it) => !it.banker || banker);
            if (items.length === 0) return null;
            const isCol = !!collapsed[g.id];
            return (
              <div className="nav-group" key={g.id}>
                <button
                  type="button"
                  className="nav-group-header"
                  onClick={(e) => { e.stopPropagation(); toggleGroup(g.id); }}
                >
                  <span className="nav-group-ic">{g.icon}</span>
                  <span className="nav-group-name">{g.title}</span>
                  <span className={isCol ? 'nav-chevron' : 'nav-chevron open'}>▸</span>
                </button>
                {!isCol && items.map((it) => (
                  <NavLink key={it.to} to={it.to} end={it.end} className={navClass}>{it.label}</NavLink>
                ))}
              </div>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          {user?.role === 'PLAYER' && <SessionTimer />}
          {user && (
            <div className="sidebar-user">
              <div className="sidebar-user-name">{user.publicName}</div>
              <div className="sidebar-user-role">{ROLE_LABELS[user.role] || user.role}</div>
            </div>
          )}
          <button className="btn btn-ghost btn-sm" onClick={handleLogout}>Выйти</button>
        </div>
      </aside>

      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
