package domain

import kotlin.random.Random

/*
Партия «Взрывных котят».

Хранит состав игроков, колоду, сброс, историю ходов и текущее
состояние. Логика turns и attacksPending - часть правил, поэтому
соответствующие методы здесь, а не в сервисе.

Инварианты:
  - players.size от 2 до 5;
  - ровно один Deck и один DiscardPile на партию;
  - winner != null, только если state == FINISHED.
*/
class Game(val id: Int) {

    var state: GameState = GameState.SETUP
        private set

    var currentTurnIndex: Int = 0
        private set

    var turnsPlayed: Int = 0
        private set

    var attacksPending: Int = 0
        private set

    var winner: Player? = null
        private set

    private val _players: MutableList<Player> = mutableListOf()
    val players: List<Player> get() = _players.toList()

    private val _moves: MutableList<Move> = mutableListOf()
    val moves: List<Move> get() = _moves.toList()

    var deck: Deck = Deck()
        private set

    var discardPile: DiscardPile = DiscardPile()
        private set

    /*
    Котёнок, которого текущий игрок только что вытянул и должен
    обезвредить (сыграть DEFUSE) или признать выбывание.
    Пока поле не null, партия «ждёт» решения текущего игрока.
    */
    var pendingKitten: Card? = null
        private set

    private var nextCardId: Int = 0

    /*
    Начинает партию: создаёт игроков, собирает колоду без котят,
    раздаёт стартовые руки, добавляет котят в колоду и переводит
    партию в IN_PROGRESS.
    */
    fun startGame(playerNames: List<String>, random: Random = Random.Default) {
        require(playerNames.size in 2..5) {
            "player count must be 2..5, was ${playerNames.size}"
        }
        check(state == GameState.SETUP) {
            "game already started"
        }

        _players.clear()
        playerNames.forEachIndexed { index, name ->
            _players += Player(id = index, name = name)
        }

        deck = buildDeckWithoutKittens(random)
        dealStartingHands()
        insertKittensIntoDeck(playerNames.size)
        deck.shuffle(random)

        recordStartSnapshot()

        state = GameState.IN_PROGRESS
        currentTurnIndex = 0
    }

    /*
    Записывает START с снапшотом начального состояния партии.
    Вызывается из startGame после раздачи и перемешивания.
    Служит опорной точкой для ReplaySystem: чтобы восстановить партию,
    нужно знать, с чего она начиналась.
    */
    private fun recordStartSnapshot() {
        val initialHands = _players.associate { it.id to it.hand }
        val initialDeckOrder = deck.peekTop(deck.size)
        addMove(
            Move(
                id = 0,
                turnNumber = 0,
                type = MoveType.START,
                initialHands = initialHands,
                initialDeckOrder = initialDeckOrder
            )
        )
    }

    fun getCurrentPlayer(): Player = _players[currentTurnIndex]

    fun getAlivePlayers(): List<Player> = _players.filter { it.isAlive }

    /*
    Партия завершена, когда остался один живой игрок.
    */
    fun isFinished(): Boolean = getAlivePlayers().size <= 1

    /*
    Завершает партию: state = FINISHED, winner = последний живой игрок.
    Если никого не осталось (теоретически) - winner = null.
    */
    fun finish() {
        state = GameState.FINISHED
        winner = getAlivePlayers().firstOrNull()
    }

    /*
    Добавляет Move в историю. ID присваивается здесь, чтобы гарантировать
    уникальность и последовательность.
    */
    fun addMove(move: Move): Move {
        val stored = move.copy(id = _moves.size + 1)
        _moves += stored
        return stored
    }

    /*
    Переходит к следующему ходу. Учитывает attacksPending:
    если у текущего игрока остались дополнительные ходы от атаки,
    ход остаётся на нём, счётчик уменьшается. Иначе - переход
    к следующему живому игроку.
    */
    fun advanceTurn() {
        turnsPlayed++

        if (attacksPending > 1) {
            attacksPending--
        } else {
            attacksPending = 0
            do {
                currentTurnIndex = (currentTurnIndex + 1) % _players.size
            } while (!_players[currentTurnIndex].isAlive)
        }
    }

    /*
    Устанавливает количество ходов, которое должен сделать текущий игрок
    из-за атаки. Вызывается валидатором при обработке Attack.
    */
    fun setAttacksPending(value: Int) {
        require(value >= 0) { "attacksPending must be non-negative, was $value" }
        attacksPending = value
    }

    /*
    Устанавливает котёнка, ожидающего обезвреживания. Вызывается
    сервисом после того, как игрок вытянул Exploding Kitten.
    */
    fun setPendingKitten(card: Card) {
        pendingKitten = card
    }

    /*
    Сбрасывает ожидание обезвреживания. Вызывается после успешного
    розыгрыша DEFUSE или после выбывания игрока.
    */
    fun clearPendingKitten() {
        pendingKitten = null
    }

    /*
    Собирает стартовую колоду без Exploding Kittens. Котята добавляются
    отдельным методом после раздачи. Итого 52 карты:
    6 Defuse, 5 Nope, 4 Attack, 4 Skip, 4 Favor, 4 Shuffle,
    5 SeeFuture, 4 × 5 кошкокарт.
    */
    private fun buildDeckWithoutKittens(random: Random): Deck {
        val cards = mutableListOf<Card>()

        fun add(type: CardType, count: Int) {
            repeat(count) {
                cards += Card(id = nextCardId++, type = type)
            }
        }

        add(CardType.DEFUSE, 6)
        add(CardType.NOPE, 5)
        add(CardType.ATTACK, 4)
        add(CardType.SKIP, 4)
        add(CardType.FAVOR, 4)
        add(CardType.SHUFFLE, 4)
        add(CardType.SEE_FUTURE, 5)
        add(CardType.CAT_BEARD, 4)
        add(CardType.CAT_TACO, 4)
        add(CardType.CAT_HAIRY_POTATO, 4)
        add(CardType.CAT_CATERMELON, 4)
        add(CardType.CAT_RAINBOW_RALPHING, 4)

        cards.shuffle(random)
        return Deck(cards)
    }

    /*
    Раздаёт каждому игроку 1 Defuse и 7 карт из колоды.
    Defuse вынимается из колоды принудительно, чтобы у каждого игрока
    он был гарантированно (правило игры).
    */
    private fun dealStartingHands() {
        repeat(_players.size) { index ->
            val defuse = removeOneCardOfTypeFromDeck(CardType.DEFUSE)
                ?: error("not enough DEFUSE cards in deck")
            _players[index].addCard(defuse)
        }

        repeat(_players.size) { index ->
            repeat(7) {
                _players[index].addCard(deck.draw())
            }
        }
    }

    /*
    Добавляет в колоду (playerCount - 1) Exploding Kittens.
    Вызывается строго после раздачи стартовых рук.
    Перемешивание выполняется отдельно в startGame.
    */
    private fun insertKittensIntoDeck(playerCount: Int) {
        val kittens = (1..playerCount - 1).map {
            Card(id = nextCardId++, type = CardType.EXPLODING_KITTEN)
        }
        val currentCards = deck.peekTop(deck.size)
        deck = Deck(currentCards + kittens)
    }

    /*
    Находит и вынимает из колоды одну карту указанного типа.
    Возвращает null, если карты нет. Используется при раздаче Defuse.
    */
    private fun removeOneCardOfTypeFromDeck(type: CardType): Card? {
        val all = deck.peekTop(deck.size).toMutableList()
        val index = all.indexOfFirst { it.type == type }
        if (index < 0) return null

        val removed = all.removeAt(index)
        deck = Deck(all)
        return removed
    }
}