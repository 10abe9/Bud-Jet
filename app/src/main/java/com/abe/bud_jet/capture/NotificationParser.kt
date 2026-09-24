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
        """(баланс|остаток|доступно|доступный|лимит|balance|available|avail\.?|limit|saldo|dostępne|dostępnych|środki|disponible)\s*[:：]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val expenseWords = Regex(
        """покупк|оплат|списан|спис\.|платеж|платёж|снятие|выдача|расход|purchase|paid|payment|spent|charged|debit|withdraw|you sent|zakup|płatnoś|platnos|obciążen|obciazen|wypłat|transakcja kart|compra|pago|cargo|retiro|gasto""",
        RegexOption.IGNORE_CASE
    )

    private val incomeWords = Regex(
        """зачислен|поступлен|пополнен|вам перевели|получен перевод|возврат|кэшбэк зачислен|received|deposit|credited|refund|sent you|incoming|wpływ|wplyw|uznanie|otrzyma|zwrot|recibid|abono|ingreso|reembolso|devoluci""",
        RegexOption.IGNORE_CASE
    )

    /**
     * One-time codes and marketing texts often contain amounts but are not payments.
     * Short words get explicit letter boundaries: `\b` treats Cyrillic differently on the
     * JVM and on Android (ICU), so it is not used with non-Latin words.
     */
    private val ignoreWords = Regex(
        """(?<!\p{L})(?:код|code|kod|código|codigo|otp|pin|пароль)(?!\p{L})|скидк|акци|предлагаем|одобрен|кредитн\p{L}* лимит|discount|offer|promo|pre-?approved|rabat|promocj|ofert|descuento|promoci""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "... в Вкусвилл", "at Netflix.com", "w BIEDRONKA", "en MERCADONA". The name ends at
     * punctuation followed by a space/end (so "Netflix.com" stays whole) or before a word
     * that starts the next part of the message.
     */
    private val merchantAfterPreposition = Regex(
        """(?:^|\s)(?:в|у|at|w|en|merchant|продавец|магазин)\s*[:：]?\s+([\p{L}\p{N}&'*._\-]+(?:\s[\p{L}\p{N}&'*._\-]+){0,3}?)(?=[.,;:!]?(?:\s|$)(?:$|\s*$|(?:баланс|остаток|доступно|карта|картой|по|с|со|balance|available|card|with|using|on|saldo|dostępne|karta|kartą|z|con|tarjeta|\d)(?!\p{L}))|[.,;:!](?:\s|$))""",
        RegexOption.IGNORE_CASE
    )

    private val upperCaseMerchant = Regex("""(?<![A-Za-z0-9])([A-Z][A-Z0-9&'*._\-]{2,}(?:\s+[A-Z0-9&'*._\-]{2,}){0,3})(?![A-Za-z0-9])""")

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
        val (amount, currency, amountEnd) = money
        if (amount <= 0.0) return ParseResult.Ignored

        val isExpense = expenseWords.containsMatchIn(full)
        val isIncome = incomeWords.containsMatchIn(full)
        val direction: Boolean? = when {
            isIncome && !isExpense -> true
            isExpense && !isIncome -> false
            else -> null
        }
        // The merchant usually follows the amount; the title often holds the card name.
        val merchant = extractMerchant(full.substring(amountEnd)) ?: extractMerchant(text.orEmpty())

        val currencyMatches = currency == null || appCurrency == null ||
            currency.equals(appCurrency, ignoreCase = true)
        return if (direction != null && currency != null && currencyMatches) {
            ParseResult.Recognized(amount, currency, direction, merchant)
        } else {
            ParseResult.Uncertain(amount, currency, direction, merchant)
        }
    }

    /** First money amount that is not labeled as a balance/limit. Requires a currency marker. */
    private fun findTransactionAmount(text: String): Triple<Double, String?, Int>? {
        for (match in moneyRegex.findAll(text)) {
            val pre = match.groups[1]?.value
            val post = match.groups[3]?.value
            val token = pre ?: post ?: continue
            val before = text.substring(0, match.range.first)
            if (balanceWords.containsMatchIn(before.takeLast(24))) continue
            val amount = AmountParser.parse(match.groupValues[2]) ?: continue
            return Triple(amount, currencyCode(token), match.range.last + 1)
        }
        return null
    }

    private fun currencyCode(token: String): String? =
        currencyTokens.firstOrNull { it.first.equals(token, ignoreCase = true) }?.second

    fun extractMerchant(text: String): String? {
        merchantAfterPreposition.find(text)?.groupValues?.get(1)?.let { candidate ->
            clean(candidate)?.let { return it }
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
