package ru.andrew.cardservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.cardservice.dto.CardLookupResponse;
import ru.andrew.cardservice.dto.CardResponse;
import ru.andrew.cardservice.entity.AccountSnapshot;
import ru.andrew.cardservice.entity.CardBinding;
import ru.andrew.cardservice.entity.CardStatus;
import ru.andrew.cardservice.exception.BadRequestException;
import ru.andrew.cardservice.exception.NotFoundException;
import ru.andrew.cardservice.repository.CardBindingRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private static final Set<String> ISSUER_ROLES = Set.of("ADMIN", "BANKER");

    private final CardBindingRepository cardRepository;
    private final AccountSnapshotService accountSnapshotService;
    private final CardEventPublisher eventPublisher;

    @Transactional
    public CardResponse issueCard(String actorRole, Long actorId,
                                  String targetPublicName, String cardUid, String notes) {
        requireIssuer(actorRole);
        if (cardUid == null || cardUid.isBlank()) {
            throw new BadRequestException("cardUid is required");
        }
        String normalizedUid = cardUid.trim();

        AccountSnapshot target = accountSnapshotService.getActiveByPublicName(targetPublicName);

        cardRepository.findByCardUid(normalizedUid).ifPresent(existing -> {
            throw new BadRequestException("cardUid is already used");
        });
        cardRepository.findFirstByAccountIdAndStatus(target.getId(), CardStatus.ISSUED)
                .ifPresent(existing -> {
                    throw new BadRequestException("Account already has an active card. Block or replace it first.");
                });

        CardBinding card = new CardBinding();
        card.setAccountId(target.getId());
        card.setCardUid(normalizedUid);
        card.setStatus(CardStatus.ISSUED);
        card.setIssuedAt(Instant.now());
        card.setIssuedByAccountId(actorId);
        card.setNotes(notes);

        CardBinding saved = cardRepository.save(card);
        eventPublisher.publishIssued(saved);
        return toResponse(saved, target);
    }

    @Transactional
    public CardResponse blockCard(String actorRole, Long actorId, Long cardId, String reason) {
        requireIssuer(actorRole);
        CardBinding card = getById(cardId);
        if (card.getStatus() != CardStatus.ISSUED) {
            throw new BadRequestException("Only ISSUED card can be blocked");
        }
        card.setStatus(CardStatus.BLOCKED);
        card.setBlockedAt(Instant.now());
        card.setBlockedByAccountId(actorId);
        card.setBlockedReason(reason);
        CardBinding saved = cardRepository.save(card);
        eventPublisher.publishBlocked(saved);
        return toResponse(saved);
    }

    @Transactional
    public CardResponse markLost(String actorRole, Long actorId, Long cardId, String reason) {
        requireIssuer(actorRole);
        CardBinding card = getById(cardId);
        if (card.getStatus() != CardStatus.ISSUED) {
            throw new BadRequestException("Only ISSUED card can be marked LOST");
        }
        card.setStatus(CardStatus.LOST);
        card.setBlockedAt(Instant.now());
        card.setBlockedByAccountId(actorId);
        card.setBlockedReason(reason);
        CardBinding saved = cardRepository.save(card);
        eventPublisher.publishLost(saved);
        return toResponse(saved);
    }

    @Transactional
    public CardResponse replaceCard(String actorRole, Long actorId, Long oldCardId,
                                    String newCardUid, String notes) {
        requireIssuer(actorRole);
        if (newCardUid == null || newCardUid.isBlank()) {
            throw new BadRequestException("newCardUid is required");
        }
        CardBinding oldCard = getById(oldCardId);
        if (oldCard.getStatus() != CardStatus.ISSUED && oldCard.getStatus() != CardStatus.LOST) {
            throw new BadRequestException("Only ISSUED or LOST cards can be replaced");
        }
        String normalizedUid = newCardUid.trim();
        cardRepository.findByCardUid(normalizedUid).ifPresent(existing -> {
            throw new BadRequestException("cardUid is already used");
        });

        oldCard.setStatus(CardStatus.REPLACED);
        if (oldCard.getBlockedAt() == null) {
            oldCard.setBlockedAt(Instant.now());
            oldCard.setBlockedByAccountId(actorId);
        }

        CardBinding newCard = new CardBinding();
        newCard.setAccountId(oldCard.getAccountId());
        newCard.setCardUid(normalizedUid);
        newCard.setStatus(CardStatus.ISSUED);
        newCard.setIssuedAt(Instant.now());
        newCard.setIssuedByAccountId(actorId);
        newCard.setNotes(notes);

        CardBinding savedNew = cardRepository.save(newCard);
        oldCard.setReplacedByCardId(savedNew.getId());
        cardRepository.save(oldCard);

        eventPublisher.publishReplaced(oldCard);
        eventPublisher.publishIssued(savedNew);

        return toResponse(savedNew);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> listByAccount(String actorRole, Long accountId) {
        requireIssuer(actorRole);
        return cardRepository.findAllByAccountIdOrderByIssuedAtDesc(accountId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CardResponse getCard(String actorRole, Long cardId) {
        requireIssuer(actorRole);
        return toResponse(getById(cardId));
    }

    /**
     * Identity resolution: a terminal (or banking) hands us a cardUid and
     * gets back the owning account. Banking can then debit by accountId,
     * a zone gate can check role + status, etc.
     */
    @Transactional(readOnly = true)
    public CardLookupResponse lookupByUid(String cardUid) {
        if (cardUid == null || cardUid.isBlank()) {
            throw new BadRequestException("cardUid is required");
        }
        CardBinding card = cardRepository.findByCardUid(cardUid.trim())
                .orElseThrow(() -> new NotFoundException("Card not found: " + cardUid));
        AccountSnapshot account = accountSnapshotService.findByIdOrNull(card.getAccountId());
        return CardLookupResponse.builder()
                .cardId(card.getId())
                .cardUid(card.getCardUid())
                .cardStatus(card.getStatus().name())
                .accountId(card.getAccountId())
                .publicName(account == null ? null : account.getPublicName())
                .accountRole(account == null ? null : account.getRole())
                .accountStatus(account == null ? null : account.getStatus())
                .build();
    }

    @Transactional
    public CardLookupResponse scanCard(String cardUid, Long terminalId, String terminalName) {
        CardLookupResponse lookup = lookupByUid(cardUid);
        CardBinding card = cardRepository.findById(lookup.getCardId()).orElseThrow();
        eventPublisher.publishScanned(card, terminalId, terminalName);
        return lookup;
    }

    private CardBinding getById(Long id) {
        if (id == null) throw new BadRequestException("cardId is required");
        return cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Card not found: " + id));
    }

    private CardResponse toResponse(CardBinding card) {
        AccountSnapshot account = accountSnapshotService.findByIdOrNull(card.getAccountId());
        return toResponse(card, account);
    }

    private CardResponse toResponse(CardBinding card, AccountSnapshot account) {
        return CardResponse.builder()
                .id(card.getId())
                .accountId(card.getAccountId())
                .publicName(account == null ? null : account.getPublicName())
                .accountRole(account == null ? null : account.getRole())
                .accountStatus(account == null ? null : account.getStatus())
                .cardUid(card.getCardUid())
                .status(card.getStatus().name())
                .issuedAt(card.getIssuedAt())
                .issuedByAccountId(card.getIssuedByAccountId())
                .blockedAt(card.getBlockedAt())
                .blockedByAccountId(card.getBlockedByAccountId())
                .blockedReason(card.getBlockedReason())
                .replacedByCardId(card.getReplacedByCardId())
                .notes(card.getNotes())
                .build();
    }

    private void requireIssuer(String role) {
        if (!ISSUER_ROLES.contains(role)) {
            throw new BadRequestException("Insufficient privileges");
        }
    }
}
