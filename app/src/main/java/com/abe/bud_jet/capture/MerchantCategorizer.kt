package com.abe.bud_jet.capture

/**
 * Picks a category for a merchant: first from what the user chose for this merchant before,
 * then from keywords mapped to built-in categories (see DefaultCategories keys).
 */
object MerchantCategorizer {

    /**
     * US merchants and generic English words. Order matters: the first matching category wins,
     * so "food" goes first to keep "Uber Eats" out of transport ("uber").
     * Keep keywords specific: they are matched as substrings of the merchant name.
     */
    private val keywordsByDefaultKey: Map<String, List<String>> = linkedMapOf(
        "food" to listOf(
            "uber eats", "uber *eats", "ubereats", "doordash", "grubhub", "instacart", "postmates",
            "cafe", "café", "coffee", "restaurant", "diner", "bakery", "pizza", "burger", "sushi",
            "grill", "bistro", "deli", "grocery", "supermarket", "starbucks", "dunkin", "mcdonald",
            "burger king", "wendy", "taco bell", "chipotle", "chick-fil-a", "subway", "domino",
            "papa john", "panera", "kfc", "popeyes", "whole foods", "trader joe", "kroger",
            "safeway", "publix", "albertsons", "costco", "aldi", "wegmans", "h-e-b", "food lion",
            "giant eagle", "sprouts", "walmart grocery", "7-eleven"
        ),
        "transport" to listOf(
            // No bare "metro"/"mobil"/"arco": they match MetroPCS, T-Mobile and "Marcos".
            "uber", "lyft", "taxi", "transit", "metrocard", "wmata", "amtrak", "greyhound",
            "parking", "parkmobile", "spothero", "toll", "e-zpass", "ezpass", "fastrak",
            "gas station", "fuel", "shell", "chevron", "exxon", "mobil oil", "bp ", "sunoco",
            "valero", "speedway", "citgo", "arco ampm", "delta air", "united airlines",
            "american airlines", "southwest air", "jetblue", "alaska air", "spirit air"
        ),
        "health" to listOf(
            "pharmacy", "cvs", "walgreens", "rite aid", "duane reade", "goodrx", "clinic",
            "hospital", "urgent care", "minuteclinic", "one medical", "medical", "dental",
            "dentist", "doctor", "labcorp", "quest diagnostics", "optometr", "kaiser", "health"
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
