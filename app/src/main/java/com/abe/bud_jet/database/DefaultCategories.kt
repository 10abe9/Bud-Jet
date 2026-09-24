package com.abe.bud_jet.database

/**
 * Built-in categories are identified by a stable key, so their names can follow the app
 * language. User-created categories have no key and keep the name the user typed.
 */
object DefaultCategories {

    data class Definition(val key: String, val isIncome: Boolean)

    val all: List<Definition> = listOf(
        Definition("food", isIncome = false),
        Definition("health", isIncome = false),
        Definition("transport", isIncome = false),
        Definition("salary", isIncome = true),
        Definition("gift", isIncome = true),
        Definition("freelance", isIncome = true)
    )

    /**
     * Every name a built-in category has been seeded with (en, ru, es, pl). Used to recognize
     * categories created before keys existed and in older backups. Keep in sync with the
     * translations when a new language is added.
     */
    val knownNames: Map<String, Set<String>> = mapOf(
        "food" to setOf("Food", "Еда", "Comida", "Jedzenie"),
        "health" to setOf("Health", "Здоровье", "Salud", "Zdrowie"),
        "transport" to setOf("Transport", "Транспорт", "Transporte"),
        "salary" to setOf("Salary", "Зарплата", "Salario", "Pensja"),
        "gift" to setOf("Gift", "Подарок", "Regalo", "Prezent"),
        "freelance" to setOf("Freelance", "Фриланс", "Freelancer")
    )

    fun isIncome(key: String): Boolean? = all.firstOrNull { it.key == key }?.isIncome

    /** Key of a built-in category recognized by its (any-language) name, or null. */
    fun keyForName(name: String, isIncome: Boolean): String? {
        val trimmed = name.trim()
        return all.firstOrNull { definition ->
            definition.isIncome == isIncome &&
                knownNames[definition.key].orEmpty().any { it.equals(trimmed, ignoreCase = true) }
        }?.key
    }
}
