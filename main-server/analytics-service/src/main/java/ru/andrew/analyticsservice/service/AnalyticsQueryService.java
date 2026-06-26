package ru.andrew.analyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.analyticsservice.entity.AccountFact;
import ru.andrew.analyticsservice.entity.MoneyFlow;
import ru.andrew.analyticsservice.entity.OrderFact;
import ru.andrew.analyticsservice.repository.AccountFactRepository;
import ru.andrew.analyticsservice.repository.MoneyFlowRepository;
import ru.andrew.analyticsservice.repository.OrderFactRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the whole economy dashboard in one pass. The dataset is small (a LARP
 * with hundreds–thousands of events), so we aggregate in memory for clarity.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private static final String CASHLESS = "CASHLESS";
    private static final String CRYPTO = "CRYPTO";
    private static final int DAYS_WINDOW = 14;

    private final MoneyFlowRepository moneyFlowRepository;
    private final OrderFactRepository orderFactRepository;
    private final AccountFactRepository accountFactRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<MoneyFlow> flows = moneyFlowRepository.findAll();
        List<OrderFact> orders = orderFactRepository.findAll();
        List<AccountFact> accounts = accountFactRepository.findAll();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", Instant.now().toString());
        out.put("money", money(flows));
        out.put("flows", flows(flows));
        out.put("accounts", accounts(accounts));
        out.put("marketplace", marketplace(orders));
        out.put("topRichest", topRichest(flows));
        out.put("topSpenders", topSpenders(orders));
        out.put("activityByDay", activityByDay(flows));
        out.put("newAccountsByDay", newAccountsByDay(accounts));
        return out;
    }

    // ---- Money supply (latest balanceAfter per account+currency) ----

    private Map<Long, BigDecimal[]> latestBalances(List<MoneyFlow> flows) {
        // [0]=cashless, [1]=crypto ; latest by occurredAt per account+currency
        Map<String, MoneyFlow> latest = new HashMap<>();
        for (MoneyFlow f : flows) {
            if (f.getAccountId() == null || f.getCurrencyType() == null || f.getBalanceAfter() == null) continue;
            String key = f.getAccountId() + "|" + f.getCurrencyType();
            MoneyFlow cur = latest.get(key);
            if (cur == null || after(f.getOccurredAt(), cur.getOccurredAt())) latest.put(key, f);
        }
        Map<Long, BigDecimal[]> byAccount = new HashMap<>();
        for (MoneyFlow f : latest.values()) {
            BigDecimal[] arr = byAccount.computeIfAbsent(f.getAccountId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (CRYPTO.equals(f.getCurrencyType())) arr[1] = f.getBalanceAfter();
            else arr[0] = f.getBalanceAfter();
        }
        return byAccount;
    }

    private Map<String, Object> money(List<MoneyFlow> flows) {
        Map<Long, BigDecimal[]> bal = latestBalances(flows);
        BigDecimal cashless = BigDecimal.ZERO;
        BigDecimal crypto = BigDecimal.ZERO;
        for (BigDecimal[] a : bal.values()) {
            cashless = cashless.add(a[0]);
            crypto = crypto.add(a[1]);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("supplyCashless", money2(cashless));
        m.put("supplyCrypto", money2(crypto));
        m.put("accountsWithBalance", bal.size());
        return m;
    }

    private List<Map<String, Object>> topRichest(List<MoneyFlow> flows) {
        Map<Long, BigDecimal[]> bal = latestBalances(flows);
        Map<Long, String> names = new HashMap<>();
        for (MoneyFlow f : flows) {
            if (f.getAccountId() != null && f.getPublicName() != null) names.put(f.getAccountId(), f.getPublicName());
        }
        return bal.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]))
                .limit(10)
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("accountId", e.getKey());
                    r.put("publicName", names.getOrDefault(e.getKey(), "#" + e.getKey()));
                    r.put("cashless", money2(e.getValue()[0]));
                    r.put("crypto", money2(e.getValue()[1]));
                    return r;
                })
                .collect(Collectors.toList());
    }

    // ---- Flows: volume, operations breakdown ----

    private Map<String, Object> flows(List<MoneyFlow> flows) {
        BigDecimal volCashless = BigDecimal.ZERO;
        BigDecimal volCrypto = BigDecimal.ZERO;
        Map<String, long[]> byOp = new LinkedHashMap<>();   // op -> [count]
        Map<String, BigDecimal> byOpAmt = new HashMap<>();
        Map<String, Long> byType = new LinkedHashMap<>();

        for (MoneyFlow f : flows) {
            BigDecimal amt = nz(f.getAmount());
            if (CRYPTO.equals(f.getCurrencyType())) volCrypto = volCrypto.add(amt);
            else volCashless = volCashless.add(amt);

            String op = f.getOperation() == null ? "—" : f.getOperation();
            byOp.computeIfAbsent(op, k -> new long[]{0})[0]++;
            byOpAmt.merge(op, amt, BigDecimal::add);

            String type = f.getEventType() == null ? "—" : f.getEventType();
            byType.merge(type, 1L, Long::sum);
        }

        List<Map<String, Object>> ops = byOp.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("operation", e.getKey());
                    r.put("count", e.getValue()[0]);
                    r.put("amount", money2(byOpAmt.getOrDefault(e.getKey(), BigDecimal.ZERO)));
                    return r;
                }).collect(Collectors.toList());

        List<Map<String, Object>> types = byType.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("type", e.getKey());
                    r.put("count", e.getValue());
                    return r;
                }).collect(Collectors.toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalOperations", flows.size());
        m.put("volumeCashless", money2(volCashless));
        m.put("volumeCrypto", money2(volCrypto));
        m.put("byOperation", ops);
        m.put("byType", types);
        return m;
    }

    // ---- Accounts (latest fact per account) ----

    private Map<String, Object> accounts(List<AccountFact> accounts) {
        Map<Long, AccountFact> latest = new HashMap<>();
        for (AccountFact a : accounts) {
            if (a.getAccountId() == null) continue;
            AccountFact cur = latest.get(a.getAccountId());
            if (cur == null || after(a.getOccurredAt(), cur.getOccurredAt())) latest.put(a.getAccountId(), a);
        }
        Map<String, Long> byRole = new LinkedHashMap<>();
        long active = 0;
        for (AccountFact a : latest.values()) {
            String role = a.getRole() == null ? "—" : a.getRole();
            byRole.merge(role, 1L, Long::sum);
            if ("ACTIVE".equals(a.getStatus())) active++;
        }
        List<Map<String, Object>> roles = byRole.entrySet().stream()
                .sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("role", e.getKey());
                    r.put("count", e.getValue());
                    return r;
                }).collect(Collectors.toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", latest.size());
        m.put("active", active);
        m.put("byRole", roles);
        return m;
    }

    // ---- Marketplace ----

    private Map<String, Object> marketplace(List<OrderFact> orders) {
        long paid = 0, cancelled = 0;
        BigDecimal revCashless = BigDecimal.ZERO, revCrypto = BigDecimal.ZERO;
        Map<String, BigDecimal> prodRev = new HashMap<>();
        Map<String, Long> prodQty = new HashMap<>();

        for (OrderFact o : orders) {
            if ("order.paid".equals(o.getEventType())) {
                paid++;
                BigDecimal tp = nz(o.getTotalPrice());
                if (CRYPTO.equals(o.getCurrencyType())) revCrypto = revCrypto.add(tp);
                else revCashless = revCashless.add(tp);
                String name = o.getProductName() == null ? "—" : o.getProductName();
                prodRev.merge(name, tp, BigDecimal::add);
                prodQty.merge(name, (long) (o.getQuantity() == null ? 0 : o.getQuantity()), Long::sum);
            } else if ("order.cancelled".equals(o.getEventType())) {
                cancelled++;
            }
        }

        List<Map<String, Object>> top = prodRev.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("name", e.getKey());
                    r.put("revenue", money2(e.getValue()));
                    r.put("quantity", prodQty.getOrDefault(e.getKey(), 0L));
                    return r;
                }).collect(Collectors.toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ordersPaid", paid);
        m.put("ordersCancelled", cancelled);
        m.put("revenueCashless", money2(revCashless));
        m.put("revenueCrypto", money2(revCrypto));
        m.put("topProducts", top);
        return m;
    }

    private List<Map<String, Object>> topSpenders(List<OrderFact> orders) {
        Map<String, BigDecimal> spend = new HashMap<>();
        for (OrderFact o : orders) {
            if (!"order.paid".equals(o.getEventType())) continue;
            String name = o.getBuyerPublicName() == null ? ("#" + o.getBuyerAccountId()) : o.getBuyerPublicName();
            spend.merge(name, nz(o.getTotalPrice()), BigDecimal::add);
        }
        return spend.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("publicName", e.getKey());
                    r.put("total", money2(e.getValue()));
                    return r;
                }).collect(Collectors.toList());
    }

    // ---- Time series ----

    private List<Map<String, Object>> activityByDay(List<MoneyFlow> flows) {
        Map<LocalDate, long[]> byDay = new TreeMap<>();      // day -> [count]
        Map<LocalDate, BigDecimal> volByDay = new TreeMap<>();
        for (MoneyFlow f : flows) {
            LocalDate d = day(f.getOccurredAt());
            byDay.computeIfAbsent(d, k -> new long[]{0})[0]++;
            volByDay.merge(d, nz(f.getAmount()), BigDecimal::add);
        }
        return lastDays(byDay.keySet()).stream().map(d -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("day", d.toString());
            r.put("operations", byDay.get(d)[0]);
            r.put("volume", money2(volByDay.getOrDefault(d, BigDecimal.ZERO)));
            return r;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> newAccountsByDay(List<AccountFact> accounts) {
        Map<LocalDate, Long> byDay = new TreeMap<>();
        for (AccountFact a : accounts) {
            if (!"account.created".equals(a.getEventType())) continue;
            byDay.merge(day(a.getOccurredAt()), 1L, Long::sum);
        }
        return lastDays(byDay.keySet()).stream().map(d -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("day", d.toString());
            r.put("count", byDay.getOrDefault(d, 0L));
            return r;
        }).collect(Collectors.toList());
    }

    private List<LocalDate> lastDays(Set<LocalDate> days) {
        return days.stream().sorted().skip(Math.max(0, days.size() - DAYS_WINDOW)).collect(Collectors.toList());
    }

    // ---- helpers ----

    private LocalDate day(Instant i) {
        return (i == null ? Instant.now() : i).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private boolean after(Instant a, Instant b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.isAfter(b);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal money2(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }
}
