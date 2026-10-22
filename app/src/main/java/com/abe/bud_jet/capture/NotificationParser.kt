package com.abe.bud_jet.capture

import com.abe.bud_jet.utils.AmountParser

/**
 * Bank-agnostic parser for payment notifications (ru/en/pl/es).
 *
 * Runs fully on-device. It looks for a money amount (a number next to a currency sign or code),
 * decides the direction from keywords and tries to extract the merchant. When it is not sure,
 * it returns [ParseResult.Uncertain] so the user can confirm instead of losing the payment.
 */
object NotificationParser {

    sealed class ParseResult {
        data class Recognized(
            val amount: Double,
            val currencyCode: String?,
            val isIncome: Boolean,
            val merchant: String?
        ) : ParseResult()

        /** Looks like a payment, but amount direction or currency needs the user's confirmation. */
        data class Uncertain(
            val amount: Double,
            val currencyCode: String?,
            val isIncome: Boolean?,
            val merchant: String?
        ) : ParseResult()

        object Ignored : ParseResult()
    }

    /** Currency tokens as written in notifications -> ISO code. Longer tokens first. */
    private val currencyTokens: List<Pair<String, String>> = listOf(
        "R$" to "BRL",
        "руб." to "RUB", "руб" to "RUB", "р." to "RUB", "р" to "RUB", "₽" to "RUB", "RUB" to "RUB",
        "RUR" to "RUB",
        "zł" to "PLN", "zl" to "PLN", "PLN" to "PLN",
        "₸" to "KZT", "тг" to "KZT", "KZT" to "KZT",
        "€" to "EUR", "EUR" to "EUR",
        "US$" to "USD", "$" to "USD", "USD" to "USD",
        "₹" to "INR", "INR" to "INR", "Rs." to "INR",
        "MXN" to "MXN", "BRL" to "BRL",
        "£" to "GBP", "GBP" to "GBP"
    )

    private val currencyAlternation = currencyTokens
        .map { Regex.escape(it.first) }
        .joinToString("|")

    private const val NUMBER = """\d{1,3}(?:[   .,']\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?"""

    /**
     * Amount with the currency after the number ("450,50 ₽") or before it ("$12.99").
     * Groups: 1 = currency before, 2 = number, 3 = currency after. Numbered groups only:
     * named groups need API 26, the app supports 24.
     */
    private val moneyRegex = Regex(
        """(?<![\p{L}\p{N}.,])(?:($currencyAlternation)\s?)?($NUMBER)(?:\s?($currencyAlternation))?(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    private val balanceWords = Regex(
        """(баланс|остаток|доступно|доступный|лимит|balance|available|avail\.?|limit|saldo|dostępne|dostępnych|środki|disponible)\s*(?:is|was|of|now)?\s*[:：]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val expenseWords = Regex(
        """покупк|оплат|списан|спис\.|платеж|платёж|снятие|выдача|расход|purchase|paid(?! you)|payment|spent|charge|debit|withdraw|you sent|transaction|with (?:visa|mastercard|amex|discover|debit|credit)|zakup|płatnoś|platnos|obciążen|obciazen|wypłat|transakcja kart|compra|pago|cargo|retiro|gasto""",
        RegexOption.IGNORE_CASE
    )

    private val incomeWords = Regex(
        """зачислен|поступлен|попол|вам перевели|получен перевод|перевод от|перевод из|входящий перевод|возврат|кэшбэк зачислен|received|deposit|credited|refund|sent you|paid you|incoming|wpływ|wplyw|uznanie|otrzyma|zwrot|recibid|abono|ingreso|reembolso|devoluci""",
        RegexOption.IGNORE_CASE
    )

    /**
     * One-time codes and marketing texts often contain amounts but are not payments.
     * Short words get explicit letter boundaries: `\b` treats Cyrillic differently on the
     * JVM and on Android (ICU), so it is not used with non-Latin words.
     */
    private val ignoreWords = Regex(
        """(?<!\p{L})(?:код|code|kod|código|codigo|otp|пароль|due|earn\p{L}*|requests?|requested)(?!\p{L})|скидк|акци|предлагаем|одобрен|кредитн\p{L}* лимит|discount(?! tire)|offer|promo|pre-?approved|declined|denied|not approved|minimum payment|will be (?:charged|debited|withdrawn|paid|made)|is scheduled|scheduled for|statement (?:is|balance)|when you open|rabat|promocj|ofert|descuento|promoci""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "... в Вкусвилл", "at Netflix.com", "w BIEDRONKA", "en MERCADONA", "with STARBUCKS",
     * "to John Smith". The name ends at punctuation followed by a space/end (so "Netflix.com"
     * stays whole) or before a word that starts the next part of the message.
     */
    private val merchantAfterPreposition = Regex(
        """(?:^|\s)(?:в|у|at|w|en|with|to|from|merchant|продавец|магазин)\s*[:：]?\s+([\p{L}\p{N}&'*._#/\-]+(?:\s[\p{L}\p{N}&'*._#/\-]+){0,3}?)(?=[.,;:!®]?(?:\s|$)(?:$|\s*$|(?:баланс|остаток|доступно|карта|картой|по|с|со|balance|available|card|with|using|on|was|is|has|for|from|ending|exceeded|via|your|you|saldo|dostępne|karta|kartą|z|con|tarjeta|\d)(?!\p{L}))|[.,;:!®](?:\s|$))""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "with"/"to"/"from" also introduce the payer's own card or account ("with your card",
     * "to your Visa", "from account ending 1234"); such candidates are not merchants.
     */
    private val notMerchantStart = Regex(
        """^(?:your|you|my|card|account|acct|checking|savings|visa|mastercard|amex|discover|debit|credit|zelle)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * P2P apps put the other person before the amount: "Jane Doe paid you $20.00",
     * "John sent you $25", "You paid Jane Doe $20.00". Group 1 or 2 is the name.
     */
    private val counterpartyBeforeAmount = Regex(
        """(?<!\p{L})(\p{Lu}[\p{L}'\-]*(?: \p{Lu}[\p{L}'.\-]*){0,3})\s+(?:sent|paid)\s+you(?!\p{L})|(?<!\p{L})[Yy]ou\s+(?:paid|sent)\s+(\p{Lu}[\p{L}'\-]*(?: \p{Lu}[\p{L}'.\-]*){0,3})\s+(?:US\$|\$|USD|€|£)"""
    )

    /**
     * Wallet apps (Google Wallet) put the merchant in the title and only "$5.45 with Visa ••1234"
     * in the text.
     */
    private val amountThenCard = Regex(
        """^\s*[+\-−]?(?:US\$|\$|€|£)?\s?[\d.,]+\s*(?:USD|EUR|GBP)?\s+(?:with|using|on|via)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val upperCaseMerchant = Regex("""(?<![A-Za-z0-9])([A-Z][A-Z0-9&'*._\-]{2,}(?:\s+[A-Z0-9&'*._\-]{2,}){0,3})(?![A-Za-z0-9])""")

    /**
     * Capitalized name right after the amount. No IGNORE_CASE here: combined with \p{Lu} it
     * behaves differently on the JVM and on Android, so both letter cases are spelled out.
     */
    private val counterpartyAfterAmount = Regex(
        """^\s*(\p{Lu}[\p{L}\p{N}&'*._\-]*(?:\s[\p{Lu}\p{N}][\p{L}\p{N}&'*._\-]*){0,2})(?=\s+(?:[Бб]аланс|[Оо]статок|[Дд]оступно|[Bb]alance|[Aa]vailable|[Ss]aldo|[Dd]ostępne)|\s*[.,;]|\s*$)"""
    )

    /** Card masks and account numbers such as "MIR-1234", "VISA4411", "*1234". */
    private val cardMask = Regex("""^[A-Z]{0,10}[-*•]?\d{2,}$""")

    /** Words that look like a merchant in upper case but are card/bank/currency markers. */
    private val notMerchant = setOf(
        "RUB", "RUR", "USD", "EUR", "PLN", "KZT", "INR", "MXN", "BRL", "GBP", "VISA", "MIR", "CARD",
        "ATM", "POS", "SMS", "MASTERCARD", "MAESTRO", "BLIK", "OTP", "PIN"
    )

    fun parse(title: String?, text: String?, appCurrency: String? = null): ParseResult {
        val full = listOfNotNull(title, text).joinToString("\n").trim()
        if (full.isEmpty()) return ParseResult.Ignored
        if (ignoreWords.containsMatchIn(full)) return ParseResult.Ignored

        val money = findTransactionAmount(full) ?: return ParseResult.Ignored
        val amount = money.amount
        val currency = money.currency
        if (amount <= 0.0) return ParseResult.Ignored

        val isExpense = expenseWords.containsMatchIn(full)
        val isIncome = incomeWords.containsMatchIn(full)
        val direction: Boolean? = when {
            // An explicit sign ("+50 ₽", "−450 ₽") is the most reliable hint.
            money.sign != null -> money.sign > 0
            isIncome && !isExpense -> true
            isExpense && !isIncome -> false
            else -> null
        }
        val amountEnd = money.end
        // The merchant usually follows the amount; the title often holds the card name.
        val merchant = extractMerchant(full.substring(amountEnd))
            ?: counterparty(full)
            ?: walletMerchant(title, text)
            ?: extractMerchant(text.orEmpty())

        val currencyMatches = currency == null || appCurrency == null ||
            currency.equals(appCurrency, ignoreCase = true)
        return if (direction != null && currency != null && currencyMatches) {
            ParseResult.Recognized(amount, currency, direction, merchant)
        } else {
            ParseResult.Uncertain(amount, currency, direction, merchant)
        }
    }

    private class Money(val amount: Double, val currency: String?, val end: Int, val sign: Int?)

    /** First money amount that is not labeled as a balance/limit. Requires a currency marker. */
    private fun findTransactionAmount(text: String): Money? {
        for (match in moneyRegex.findAll(text)) {
            val pre = match.groups[1]?.value
            val post = match.groups[3]?.value
            val token = pre ?: post ?: continue
            val before = text.substring(0, match.range.first)
            if (balanceWords.containsMatchIn(before.takeLast(24))) continue
            val amount = parseAmount(match.groupValues[2]) ?: continue
            val sign = when (before.trimEnd().lastOrNull()) {
                '+' -> 1
                '-', '−', '–' -> -1
                else -> null
            }
            return Money(amount, currencyCode(token), match.range.last + 1, sign)
        }
        return null
    }

    /**
     * Money has at most two decimals, so "$2,500" and "1.234 zł" are thousands, not 2.5 and
     * 1.234 (AmountParser reads a single separator as the decimal one, as users type it).
     */
    private fun parseAmount(raw: String): Double? =
        if (groupedInteger.matches(raw)) raw.filter { it.isDigit() }.toDoubleOrNull() else AmountParser.parse(raw)

    private val groupedInteger = Regex("""\d{1,3}(?:[\s.,'\u00A0\u202F]\d{3})+""")

    private fun currencyCode(token: String): String? =
        currencyTokens.firstOrNull { it.first.equals(token, ignoreCase = true) }?.second

    private fun counterparty(text: String): String? =
        counterpartyBeforeAmount.find(text)?.groupValues
            ?.let { it[1].ifEmpty { it[2] } }
            ?.let(::clean)

    private fun walletMerchant(title: String?, text: String?): String? {
        if (title.isNullOrBlank() || text == null || !amountThenCard.containsMatchIn(text)) return null
        return clean(title)
    }

    fun extractMerchant(text: String): String? {
        merchantAfterPreposition.findAll(text)
            .map { it.groupValues[1] }
            .firstOrNull { !notMerchantStart.containsMatchIn(it) }
            ?.let { candidate -> clean(candidate)?.let { return it } }
        // SMS style: "перевод 50р Т-Банк Баланс: ..." - a capitalized name right after the amount.
        counterpartyAfterAmount.find(text)?.groupValues?.get(1)?.let { candidate ->
            if (!balanceWords.containsMatchIn(candidate) && candidate.uppercase() !in notMerchant) {
                clean(candidate)?.let { return it }
            }
        }
        return upperCaseMerchant.findAll(text)
            .map { it.groupValues[1] }
            .firstOrNull { candidate ->
                candidate.split(" ").none { it in notMerchant || cardMask.matches(it) } &&
                    candidate.any { it.isLetter() }
            }
            ?.let(::clean)
    }

    private fun clean(raw: String): String? {
        val trimmed = raw.trim().trim('.', ',', '-', '*', ' ')
        if (trimmed.length < 2) return null
        if (trimmed.none { it.isLetter() }) return null
        return trimmed.take(40)
    }
}
