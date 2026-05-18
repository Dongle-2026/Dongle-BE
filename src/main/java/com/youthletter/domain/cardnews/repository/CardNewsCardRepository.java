package com.youthletter.domain.cardnews.repository;

import com.youthletter.domain.cardnews.entity.CardNewsCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardNewsCardRepository extends JpaRepository<CardNewsCard, Long> {

    List<CardNewsCard> findByCardNewsIdOrderByCardOrderAsc(Long cardNewsId);

    Optional<CardNewsCard> findByCardNewsIdAndCardOrder(Long cardNewsId, int cardOrder);
}
