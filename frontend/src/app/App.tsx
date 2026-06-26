import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from '../shared/auth/AuthContext';
import { PrivateRoute, RoleRoute } from '../shared/auth/guards';
import { LoadingBlock } from '../shared/ui';
import RootLayout from './RootLayout';

// Eagerly imported: first paint.
import LoginPage from '../features/auth/LoginPage';

const HomePage = lazy(() => import('./HomePage'));

// Lazily imported feature pages: each feature area becomes its own chunk.
const DashboardPage = lazy(() => import('../features/admin/pages/DashboardPage'));
const AccountsPage = lazy(() => import('../features/admin/pages/AccountsPage'));
const AccountDetailPage = lazy(() => import('../features/admin/pages/AccountDetailPage'));
const CreateAccountPage = lazy(() => import('../features/admin/pages/CreateAccountPage'));
const EditAccountPage = lazy(() => import('../features/admin/pages/EditAccountPage'));
const ChangeStatusPage = lazy(() => import('../features/admin/pages/ChangeStatusPage'));
const IssuePinPage = lazy(() => import('../features/admin/pages/IssuePinPage'));
const PinsPage = lazy(() => import('../features/admin/pages/PinsPage'));

const CardsPage = lazy(() => import('../features/cards/pages/CardsPage'));
const AuditPage = lazy(() => import('../features/audit/pages/AuditPage'));
const AnalyticsPage = lazy(() => import('../features/analytics/pages/AnalyticsPage'));
const TerminalsPage = lazy(() => import('../features/terminals/pages/TerminalsPage'));
const PosTerminalPage = lazy(() => import('../features/terminals/pages/PosTerminalPage'));
const BankingHomePage = lazy(() => import('../features/banking/pages/BankingHomePage'));
const BalancePage = lazy(() => import('../features/banking/pages/BalancePage'));
const HistoryPage = lazy(() => import('../features/banking/pages/HistoryPage'));
const DepositPage = lazy(() => import('../features/banking/pages/DepositPage'));
const WithdrawPage = lazy(() => import('../features/banking/pages/WithdrawPage'));
const TransferPage = lazy(() => import('../features/banking/pages/TransferPage'));
const ReversePage = lazy(() => import('../features/banking/pages/ReversePage'));
const MassEventPage = lazy(() => import('../features/banking/pages/MassEventPage'));
const PayPage = lazy(() => import('../features/banking/pages/PayPage'));
const CryptoExchangePage = lazy(() => import('../features/banking/pages/CryptoExchangePage'));
// Credit (deposits/loans) hidden from the UI for now — backend stays; re-add route + nav to restore.
// const CreditPage = lazy(() => import('../features/banking/pages/CreditPage'));

const StorefrontPage = lazy(() => import('../features/marketplace/pages/StorefrontPage'));
const MyOrdersPage = lazy(() => import('../features/marketplace/pages/MyOrdersPage'));
const InventoryPage = lazy(() => import('../features/marketplace/pages/InventoryPage'));
const ProductsAdminPage = lazy(() => import('../features/marketplace/pages/ProductsAdminPage'));
const OrdersAdminPage = lazy(() => import('../features/marketplace/pages/OrdersAdminPage'));
const ScenariosPage = lazy(() => import('../features/marketplace/pages/ScenariosPage'));

const NewsFeedPage = lazy(() => import('../features/news/pages/NewsFeedPage'));
const NewsDetailPage = lazy(() => import('../features/news/pages/NewsDetailPage'));
const NewsAdminPage = lazy(() => import('../features/news/pages/NewsAdminPage'));

const OPERATOR = ['ADMIN', 'BANKER', 'DEVELOPER'];
const BANKER = ['ADMIN', 'BANKER'];

export default function App() {
  return (
    <AuthProvider>
      <Suspense fallback={<LoadingBlock />}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route
            element={
              <PrivateRoute>
                <RootLayout />
              </PrivateRoute>
            }
          >
            <Route index element={<HomePage />} />

            {/* ---- Self service (any authenticated user) ---- */}
            <Route path="me/balance" element={<BalancePage selfOnly />} />
            <Route path="me/history" element={<HistoryPage selfOnly />} />
            <Route path="me/pay" element={<PayPage />} />
            <Route path="crypto" element={<CryptoExchangePage />} />

            {/* ---- Admin: account management ---- */}
            <Route path="dashboard" element={<RoleRoute roles={OPERATOR}><DashboardPage /></RoleRoute>} />
            <Route path="accounts" element={<RoleRoute roles={OPERATOR}><AccountsPage /></RoleRoute>} />
            <Route path="accounts/new" element={<RoleRoute roles={OPERATOR}><CreateAccountPage /></RoleRoute>} />
            <Route path="pins" element={<RoleRoute roles={OPERATOR}><PinsPage /></RoleRoute>} />
            <Route path="accounts/:id" element={<RoleRoute roles={OPERATOR}><AccountDetailPage /></RoleRoute>} />
            <Route path="accounts/:id/edit" element={<RoleRoute roles={OPERATOR}><EditAccountPage /></RoleRoute>} />
            <Route path="accounts/:id/status" element={<RoleRoute roles={OPERATOR}><ChangeStatusPage /></RoleRoute>} />
            <Route path="accounts/:id/pin" element={<RoleRoute roles={OPERATOR}><IssuePinPage /></RoleRoute>} />

            {/* ---- Cards (banker/admin) ---- */}
            <Route path="cards" element={<RoleRoute roles={BANKER}><CardsPage /></RoleRoute>} />

            {/* ---- Audit log (banker/admin) ---- */}
            <Route path="audit" element={<RoleRoute roles={BANKER}><AuditPage /></RoleRoute>} />

            {/* ---- Analytics (banker/admin) ---- */}
            <Route path="analytics" element={<RoleRoute roles={BANKER}><AnalyticsPage /></RoleRoute>} />

            {/* ---- Terminals (view: banker/admin; manage: admin) ---- */}
            <Route path="terminals" element={<RoleRoute roles={BANKER}><TerminalsPage /></RoleRoute>} />
            <Route path="terminals/pos" element={<RoleRoute roles={BANKER}><PosTerminalPage /></RoleRoute>} />

            {/* ---- Marketplace (browse/buy: any authenticated; manage: banker/admin) ---- */}
            <Route path="market" element={<StorefrontPage />} />
            <Route path="market/orders" element={<MyOrdersPage />} />
            <Route path="market/inventory" element={<InventoryPage />} />
            <Route path="market/products" element={<RoleRoute roles={BANKER}><ProductsAdminPage /></RoleRoute>} />
            <Route path="market/scenarios" element={<RoleRoute roles={BANKER}><ScenariosPage /></RoleRoute>} />
            <Route path="market/manage-orders" element={<RoleRoute roles={BANKER}><OrdersAdminPage /></RoleRoute>} />

            {/* ---- News (read: any authenticated; manage: banker/admin) ---- */}
            <Route path="news" element={<NewsFeedPage />} />
            <Route path="news/manage" element={<RoleRoute roles={BANKER}><NewsAdminPage /></RoleRoute>} />
            <Route path="news/:id" element={<NewsDetailPage />} />

            {/* ---- Banking operations (banker/admin) ---- */}
            <Route path="bank" element={<RoleRoute roles={BANKER}><BankingHomePage /></RoleRoute>} />
            <Route path="bank/balance" element={<RoleRoute roles={BANKER}><BalancePage /></RoleRoute>} />
            <Route path="bank/history" element={<RoleRoute roles={BANKER}><HistoryPage /></RoleRoute>} />
            <Route path="bank/deposit" element={<RoleRoute roles={BANKER}><DepositPage /></RoleRoute>} />
            <Route path="bank/withdraw" element={<RoleRoute roles={BANKER}><WithdrawPage /></RoleRoute>} />
            <Route path="bank/transfer" element={<RoleRoute roles={BANKER}><TransferPage /></RoleRoute>} />
            <Route path="bank/reverse" element={<RoleRoute roles={BANKER}><ReversePage /></RoleRoute>} />
            <Route path="bank/event" element={<RoleRoute roles={BANKER}><MassEventPage /></RoleRoute>} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </AuthProvider>
  );
}
