package com.abe.bud_jet.capture

import com.abe.bud_jet.capture.NotificationParser.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {

    private fun recognized(title: String?, text: String, currency: String? = null): ParseResult.Recognized {
        val result = NotificationParser.parse(title, text, currency)
        assertTrue("expected Recognized but was $result", result is ParseResult.Recognized)
        return result as ParseResult.Recognized
    }

    @Test
    fun russianPurchaseSkipsBalance() {
        val r = recognized("MIR-1234", "Покупка 450р PYATEROCHKA Баланс: 12 345,67р", "RUB")
        assertEquals(450.0, r.amount, 0.0)
        assertEquals("RUB", r.currencyCode)
        assertEquals(false, r.isIncome)
        assertEquals("PYATEROCHKA", r.merchant)
    }

    @Test
    fun russianBalanceFirstStillFindsPurchase() {
        val r = recognized(null, "Остаток 5 000 ₽. Оплата 1 250,50 ₽ в Вкусвилл", "RUB")
        assertEquals(1250.5, r.amount, 0.0)
        assertEquals("Вкусвилл", r.merchant)
    }

    @Test
    fun russianIncome() {
        val r = recognized("Зачисление", "Зачисление 45 000 ₽ от ИВАН И.", "RUB")
        assertEquals(45000.0, r.amount, 0.0)
        assertEquals(true, r.isIncome)
    }

    @Test
    fun englishCardPayment() {
        val r = recognized("Google Wallet", "You paid $12.99 at Netflix.com", "USD")
        assertEquals(12.99, r.amount, 0.0)
        assertEquals(false, r.isIncome)
        assertEquals("Netflix.com", r.merchant)
    }

    @Test
    fun englishReceived() {
        val r = recognized("PayPal", "You've received €25.00 from John", "EUR")
        assertEquals(25.0, r.amount, 0.0)
        assertEquals(true, r.isIncome)
    }

    @Test
    fun polishCardTransaction() {
        val r = recognized("mBank", "Transakcja kartą: 89,99 PLN w BIEDRONKA 123. Dostępne: 1 204,11 PLN", "PLN")
        assertEquals(89.99, r.amount, 0.0)
        assertEquals("PLN", r.currencyCode)
        assertEquals(false, r.isIncome)
    }

    @Test
    fun spanishPurchase() {
        val r = recognized(null, "Compra de 23,40 € en MERCADONA con tu tarjeta", "EUR")
        assertEquals(23.4, r.amount, 0.0)
        assertEquals(false, r.isIncome)
        assertEquals("MERCADONA", r.merchant)
    }

    @Test
    fun foreignCurrencyNeedsConfirmation() {
        val result = NotificationParser.parse(null, "Покупка 15.00 USD APPLE.COM/BILL", "RUB")
        assertTrue(result is ParseResult.Uncertain)
        assertEquals("USD", (result as ParseResult.Uncertain).currencyCode)
    }

    @Test
    fun unknownDirectionNeedsConfirmation() {
        val result = NotificationParser.parse("Bank", "Операция 700 ₽ OZON", "RUB")
        assertTrue(result is ParseResult.Uncertain)
        assertNull((result as ParseResult.Uncertain).isIncome)
    }

    @Test
    fun ignoresCodesPromosAndTextsWithoutMoney() {
        assertTrue(NotificationParser.parse(null, "Код подтверждения: 4821. Покупка 500 ₽", "RUB") is ParseResult.Ignored)
        assertTrue(NotificationParser.parse(null, "Скидка 500 ₽ на первый заказ", "RUB") is ParseResult.Ignored)
        assertTrue(NotificationParser.parse("Чат", "Встреча в 15:30, возьми 2 товара", "RUB") is ParseResult.Ignored)
        assertTrue(NotificationParser.parse(null, "", "RUB") is ParseResult.Ignored)
    }

    @Test
    fun wordEndingWithCurrencyLetterIsNotMoney() {
        // "товар 5" must not be read as "5 р."
        assertTrue(NotificationParser.parse(null, "Покупка: товар 5 шт", "RUB") is ParseResult.Ignored)
    }

    @Test
    fun topUpIsIncomeEvenWithTypo() {
        assertEquals(true, recognized("Т-Банк", "Пополение 50 Р ...", "RUB").isIncome)
        assertEquals(true, recognized("Т-Банк", "Пополнение 50 ₽ с карты Сбербанк", "RUB").isIncome)
    }

    @Test
    fun explicitSignWins() {
        assertEquals(true, recognized("Bank", "Операция +1 500 ₽ OZON", "RUB").isIncome)
        assertEquals(false, recognized("Bank", "Операция −700 ₽ OZON", "RUB").isIncome)
    }

    @Test
    fun smsTransferNeedsConfirmationButKeepsCounterparty() {
        val result = NotificationParser.parse(
            "900",
            "Счет карты VISA1234 15:28 перевод 50р Т-Банк Баланс: 54.90р",
            "RUB"
        )
        assertTrue(result is ParseResult.Uncertain)
        result as ParseResult.Uncertain
        assertEquals(50.0, result.amount, 0.0)
        assertEquals("Т-Банк", result.merchant)
    }

    @Test
    fun incomingTransfer() {
        assertEquals(true, recognized(null, "Перевод от Иван И. 2 000 ₽", "RUB").isIncome)
    }
}
