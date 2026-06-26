# Banking API Human UI

Простой React + TypeScript + Vite интерфейс для ручной работы с banking backend в человеческом виде.

## Запуск

```bash
cp env.example .env
npm install
npm run dev
```

## Что сделано

- Вход администратора и игрока
- Хранение JWT в localStorage
- Автоподстановка `Authorization: Bearer <token>`
- Очистка токена и возврат на экран входа при `401`
- Просмотр баланса
- Просмотр истории операций без вывода pageable/json в интерфейсе
- Человеческие формы: пополнение, списание, перевод, ручная корректировка, отмена операции
- Минимальный и понятный UI без debug-полей, без idempotency key в интерфейсе

## Что можно подправить

- `src/config.ts` — базовый URL и endpoint mapping
- `src/App.tsx` — порядок секций и подписи
- `src/types.ts` — если backend вернёт дополнительные поля
- `src/styles.css` — внешний вид
- `src/utils.ts` — правила преобразования типов операций и валют в человекочитаемые подписи
