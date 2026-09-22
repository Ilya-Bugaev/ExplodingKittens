package app.storage

import app.dto.GameSummary
import domain.Card
import domain.Game
import domain.Move

fun Card.toRecord(): CardRecord = CardRecord(id = id, type = type.name)

fun Move.toRecord(): MoveRecord = MoveRecord(
    id = id,
    turnNumber = turnNumber,
    type = type.name,
    authorId = author?.id,
    targetId = target?.id,
    cardsPlayed = cardsPlayed.map { it.toRecord() },
    drawnCard = drawnCard?.toRecord(),
    receivedCard = receivedCard?.toRecord(),
    requestedCardType = requestedCardType?.name,
    placedKittenPosition = placedKittenPosition,
    eliminated = eliminated
)

fun Game.toRecord(): GameRecord = GameRecord(
    gameId = id,
    playerNames = players.map { it.name },
    winnerName = winner?.name,
    turnsPlayed = turnsPlayed,
    moves = moves.map { it.toRecord() }
)

fun GameRecord.toSummary(): GameSummary = GameSummary(
    gameId = gameId,
    playerNames = playerNames,
    winnerName = winnerName,
    turnsPlayed = turnsPlayed
)