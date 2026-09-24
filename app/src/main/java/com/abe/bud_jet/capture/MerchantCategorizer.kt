package com.abe.bud_jet.capture

/**
 * Picks a category for a merchant: first from what the user chose for this merchant before,
 * then from keywords mapped to built-in categories (see DefaultCategories keys).
 */
object MerchantCategorizer {

    private val keywordsByDefaultKey: Map<String, List<String>> = mapOf(
        "transport" to listOf(
            "taxi", "такси", "uber", "bolt", "yandex go", "yandex*go", "яндекс go", "citymobil",
            "metro", "метро", "транспорт", "parking", "парковк", "азс", "fuel", "shell", "lukoil",
            "лукойл", "orlen", "bp ", "gazprom", "газпромнефть", "ржд", "rzd", "aeroflot", "аэрофлот",
            "renfe", "cabify", "freenow", "jakdojade", "mpk", "ztm"
        ),
        "food" to listOf(
            "кафе", "cafe", "café", "coffee", "кофе", "ресторан", "restaurant", "restauracja",
            "restaurante", "pizza", "пицц", "burger", "бургер", "mcdonald", "kfc", "вкусно",
            "starbucks", "пятерочка", "пятёрочка", "pyaterochka", "magnit", "магнит", "перекресток",
            "перекрёсток", "perekrestok", "vkusvill", "вкусвилл", "лента", "lenta", "ашан", "auchan",
            "dixy", "дикси", "biedronka", "lidl", "żabka", "zabka", "carrefour", "mercadona",
            "walmart", "продукт", "grocery", "supermarket", "market", "delivery club", "самокат",
            "samokat", "glovo", "wolt", "uber eats", "doordash", "bakery", "пекарн", "sushi", "суши"
        ),
        "health" to listOf(
            "аптек", "apteka", "pharmacy", "farmacia", "клиник", "clinic", "klinika", "стоматолог",
            "dentist", "hospital", "больниц", "медси", "invitro", "инвитро", "lab", "здоров", "health"
        )
    )

    /** Normalized key used to remember the user's category choice per merchant. */
    fun merchantKey(merchant: String): String =
        merchant.lowercase()
            .replace(Regex("""[^\p{L}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Built-in category key guessed from the merchant name, or null. */
    fun guessDefaultKey(merchant: String): String? {
        val lower = merchant.lowercase()
        return keywordsByDefaultKey.entries.firstOrNull { (_, words) ->
            words.any { lower.contains(it) }
        }?.key
    }
}
