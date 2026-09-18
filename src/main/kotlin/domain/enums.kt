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
    CAT_RAINBOW_RALPHING
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