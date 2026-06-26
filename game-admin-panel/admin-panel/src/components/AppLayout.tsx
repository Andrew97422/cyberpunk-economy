import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABELS } from '../utils/helpers'

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-logo">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none" aria-label="Логотип">
            <rect width="28" height="28" rx="6" fill="currentColor" opacity="0.15"/>
            <path d="M7 14L11 18L21 8" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span>Admin Panel</span>
        </div>

        <nav className="sidebar-nav">
          <NavLink to="/dashboard" className={({isActive}) => isActive ? 'nav-link active' : 'nav-link'}>
            Главная
          </NavLink>
          <NavLink to="/accounts" className={({isActive}) => isActive ? 'nav-link active' : 'nav-link'}>
            Аккаунты
          </NavLink>
          <NavLink to="/accounts/new" className={({isActive}) => isActive ? 'nav-link active' : 'nav-link'}>
            Создать аккаунт
          </NavLink>
        </nav>

        <div className="sidebar-footer">
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
  )
}
