# Game Admin Panel

Внутренняя админ-панель для управления аккаунтами. React + TypeScript + Vite.

## Быстрый старт

```bash
cp .env.example .env
# Отредактируй VITE_API_BASE_URL в .env

npm install
npm run dev
# Открой http://localhost:5174
```

## Структура

```
src/
  api/
    config.ts       ← ВСЕ endpoint-ы здесь. Меняй пути здесь.
    client.ts       ← axios instance с Bearer token и 401-перехватом
  auth/
    AuthContext.tsx ← login/logout, хранение user в localStorage
    PrivateRoute.tsx
  components/
    AppLayout, StatusBadge, FormField, SelectField,
    LoadingBlock, ErrorBlock, SuccessMessage, ConfirmDialog,
    AccountsTable, PageSection
  features/ (зарезервировано)
  pages/
    LoginPage, DashboardPage, AccountsPage, AccountDetailPage,
    CreateAccountPage, EditAccountPage, ChangePasswordPage, ChangeStatusPage
  types/
    index.ts        ← все TS-интерфейсы и enum значения
  utils/
    helpers.ts      ← formatDate, extractError, ROLE_LABELS, STATUS_LABELS
  styles/
    global.css
```

## Роуты

| Путь                      | Страница                    |
|---------------------------|-----------------------------|
| /login                    | Вход                        |
| /dashboard                | Главная / сводка            |
| /accounts                 | Список аккаунтов            |
| /accounts/new             | Создать аккаунт             |
| /accounts/:id             | Профиль аккаунта            |
| /accounts/:id/edit        | Редактирование              |
| /accounts/:id/password    | Создание PIN для входа      |
| /accounts/:id/status      | Изменение статуса           |

## Что потребует правок под твой бекенд

### 1. Базовый URL
В `.env`:
```
VITE_API_BASE_URL=http://твой-сервер/api
```

### 2. Endpoint-ы
Все пути — в `src/api/config.ts` в объекте `ENDPOINTS`.
Изменил путь на бекенде → поменяй одну строчку здесь.

### 3. Смена пароля
В OpenAPI нет `PATCH /accounts/:id/password`.
Сейчас реализовано через `POST /admin/pins` (временный PIN).
Если добавишь endpoint — в `ChangePasswordPage.tsx` замени логику на:
```ts
await client.patch(ENDPOINTS.updatePassword(id), { password: newPassword })
```
И добавь `updatePassword: (id) => \`/accounts/\${id}/password\`` в `config.ts`.

### 4. Обновление полей (publicName, characterName, notes)
В OpenAPI нет `PATCH /accounts/:id` для этих полей.
В `EditAccountPage` эти поля disabled и помечены комментарием.
Как только появится endpoint — убери `disabled` и добавь вызов.

### 5. Фильтрация по role/status
Сейчас фильтрация клиентская (на той странице данных, что пришла).
Если бекенд добавит `?role=ADMIN&status=ACTIVE` в `GET /accounts` —
добавь эти параметры в `AccountsPage.tsx` в объект `params`.

### 6. Enums
Роли: `PLAYER | BANKER | ADMIN | DEVELOPER`
Статусы: `ACTIVE | BLOCKED | ARCHIVED`
Менять в `src/types/index.ts` — `ACCOUNT_ROLES` и `ACCOUNT_STATUSES`.

### 7. Транзакции и баланс
На странице профиля аккаунта уже подключены `balance` и `activeSessions`.
Для истории транзакций — добавь роут `/accounts/:id/transactions`
и вызов `ENDPOINTS.transactions(id)`.

## Используемые endpoint-ы OpenAPI

| Действие              | Метод  | Путь                        |
|-----------------------|--------|------------------------------|
| Вход (admin)          | POST   | /auth/admin/login            |
| Выход                 | POST   | /auth/logout                 |
| Текущий пользователь  | GET    | /auth/me                     |
| Список аккаунтов      | GET    | /accounts                    |
| Аккаунт по ID         | GET    | /accounts/:id                |
| Создать аккаунт       | POST   | /accounts                    |
| Изменить статус       | PATCH  | /accounts/:id/status         |
| Изменить роль         | PATCH  | /accounts/:id/role           |
| Баланс аккаунта       | GET    | /banking/balance/:id         |
| Активные сессии       | GET    | /sessions/active             |
| Завершить сессию      | POST   | /sessions/:id/terminate      |
| Создать PIN           | POST   | /admin/pins                  |
| Отозвать PIN          | POST   | /admin/pins/:id/revoke       |
