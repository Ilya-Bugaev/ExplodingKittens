package domain

enum class CardType {
    EXPLODING_KITTEN,
    DEFUSE,
    NOPE,
    ATTACK,
    SKIP,
    FAVOR,
    SHUFFLE,
    SEE_FUTURE,
    CAT_BEARD,
    CAT_TACO,
    CAT_HAIRY_POTATO,
    CAT_CATERMELON,
    CAT_RAINBOW_RALPHING;

    /*
    Кошкокарты бесполезны сами по себе — они работают только в комбинациях.
    Проверка используется в EKMoveValidator при валидации одиночного розыгрыша.
    */
    val isCatCard: Boolean
        get() = when (this) {
            CAT_BEARD,
            CAT_TACO,
            CAT_HAIRY_POTATO,
            CAT_CATERMELON,
            CAT_RAINBOW_RALPHING -> true
            else -> false
        }
}

enum class MoveType {
    START,
    DRAW,
    PLAY_CARD,
    PLAY_TWO_OF_A_KIND,
    PLAY_THREE_OF_A_KIND,
    PLAY_FIVE_DIFFERENT,
    RESOLVE_PENDING
}

enum class GameState {
    SETUP,
    IN_PROGRESS,
    FINISHED
}

fun CardType.isCatCard(): Boolean = when (this) {
    CardType.CAT_BEARD,
    CardType.CAT_TACO,
    CardType.CAT_HAIRY_POTATO,
    CardType.CAT_CATERMELON,
    CardType.CAT_RAINBOW_RALPHING -> true
    else -> false
}