package domain

enum class CardType(val displayName: String) {
    EXPLODING_KITTEN("Exploding Kitten"),
    DEFUSE("Defuse"),
    NOPE("Nope"),
    ATTACK("Attack"),
    SKIP("Skip"),
    FAVOR("Favor"),
    SHUFFLE("Shuffle"),
    SEE_FUTURE("See the Future"),
    CAT_BEARD("Beard Cat"),
    CAT_TACO("Taco Cat"),
    CAT_HAIRY_POTATO("Hairy Potato Cat"),
    CAT_CATERMELON("Catermelon"),
    CAT_RAINBOW_RALPHING("Rainbow Ralphing Cat");

    /*
    Кошкокарты бесполезны сами по себе - они работают только в комбинациях.
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