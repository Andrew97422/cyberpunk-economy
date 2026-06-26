# Event Catalog

## Topic: banking.events.v1

### Common envelope
- eventId: String (UUID)
- eventType: String
- aggregateType: String
- aggregateId: String
- source: String
- version: String
- occurredAt: LocalDateTime
- payload: Object

---

### Event: balance.deposited
- version: v1
- producer: main-server.banking
- aggregateType: BALANCE
- payload:
    - transactionId: Long
    - accountId: Long
    - publicName: String
    - relatedAccountId: Long?
    - relatedPublicName: String?
    - currencyType: String
    - operation: String
    - amount: BigDecimal
    - balanceAfter: BigDecimal
    - actorId: Long
    - occurredAt: LocalDateTime

### Event: balance.withdrawn
- version: v1
- producer: main-server.banking
- aggregateType: BALANCE
- payload: same as BankingEventPayload

### Event: balance.adjusted
- version: v1
- producer: main-server.banking
- aggregateType: BALANCE
- payload: same as BankingEventPayload

### Event: balance.transferred
- version: v1
- producer: main-server.banking
- aggregateType: BALANCE
- payload: same as BankingEventPayload

### Event: transaction.reversed
- version: v1
- producer: main-server.banking
- aggregateType: BALANCE
- payload: same as BankingEventPayload