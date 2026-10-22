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

    private fun assertExpense(title: String?, text: String, amount: Double, merchant: String?) {
        val r = recognized(title, text, "USD")
        assertEquals(text, amount, r.amount, 0.0)
        assertEquals(text, "USD", r.currencyCode)
        assertEquals(text, false, r.isIncome)
        assertEquals(text, merchant, r.merchant)
    }

    private fun assertIncome(title: String?, text: String, amount: Double, merchant: String?) {
        val r = recognized(title, text, "USD")
        assertEquals(text, amount, r.amount, 0.0)
        assertEquals(text, true, r.isIncome)
        assertEquals(text, merchant, r.merchant)
    }

    @Test
    fun usBankCardAlerts() {
        assertExpense("Chase", "You made a \$45.67 transaction with STARBUCKS on your Chase Sapphire card.", 45.67, "STARBUCKS")
        assertExpense("Chase", "A \$1,234.56 transaction with AMAZON MKTPL*2K4 was made on your credit card ending in 1234.", 1234.56, "AMAZON MKTPL*2K4")
        assertExpense("Chase", "You made a \$45.67 transaction with STARBUCKS. Your available balance is \$312.08.", 45.67, "STARBUCKS")
        assertExpense("Bank of America", "A purchase of \$25.00 was made at AMAZON.COM with your card ending in 1234.", 25.0, "AMAZON.COM")
        assertExpense("Bank of America", "Your debit card ending in 1234 was used for \$62.18 at TARGET T-1849.", 62.18, "TARGET T-1849")
        assertExpense("Wells Fargo", "Wells Fargo card purchase: \$45.00 at WALMART SUPERCENTER on card ending 1234", 45.0, "WALMART SUPERCENTER")
        assertExpense("Capital One", "A transaction of \$12.34 was charged to your Quicksilver card at STARBUCKS.", 12.34, "STARBUCKS")
        assertExpense("Capital One", "Capital One: A charge of \$8.99 was made at SPOTIFY USA on your Venture card ending in 1234.", 8.99, "SPOTIFY USA")
        assertExpense("Citi", "A \$50.00 transaction was made at TARGET on card ending in 1234.", 50.0, "TARGET")
        assertExpense("American Express", "Large Purchase Approved: \$123.45 at BEST BUY", 123.45, "BEST BUY")
        assertExpense("Discover", "Transaction Alert: A transaction of \$45.00 at STARBUCKS was made on your Discover card.", 45.0, "STARBUCKS")
        assertExpense("U.S. Bank", "PIN purchase of \$40.00 at KROGER", 40.0, "KROGER")
        assertExpense("Chime", "You spent \$12.34 at WALMART", 12.34, "WALMART")
        assertExpense("Chase", "A \$9.99 recurring payment to APPLE.COM/BILL was made.", 9.99, "APPLE.COM/BILL")
        assertExpense("Wells Fargo", "ATM withdrawal of \$100.00 from account ending 5678.", 100.0, null)
    }

    @Test
    fun usIncomeAndRefunds() {
        assertIncome("Chime", "Your direct deposit of \$1,234.56 has arrived!", 1234.56, null)
        assertIncome("Discover", "A refund of \$19.99 from AMAZON was credited to your Discover card.", 19.99, "AMAZON")
        assertIncome("Zelle", "You received \$50.00 from John Smith.", 50.0, "John Smith")
    }

    @Test
    fun walletMerchantComesFromTitle() {
        assertExpense("Starbucks", "\$5.45 with Visa \u2022\u2022\u2022\u2022 1234", 5.45, "Starbucks")
        assertExpense("Trader Joe's", "\$38.12 with Mastercard \u2022\u20224411", 38.12, "Trader Joe's")
    }

    @Test
    fun peerToPeerApps() {
        assertExpense("Venmo", "You paid Jane Doe \$20.00", 20.0, "Jane Doe")
        assertIncome("Venmo", "Jane Doe paid you \$20.00", 20.0, "Jane Doe")
        assertIncome("Cash App", "John sent you \$25 for pizza", 25.0, "John")
        assertExpense("Chase", "You sent \$50.00 to John Smith with Zelle\u00AE.", 50.0, "John Smith")
        assertIncome("Chase", "John Smith sent you \$50.00 with Zelle\u00AE.", 50.0, "John Smith")
        assertExpense("PayPal", "You paid \$12.99 USD to Netflix", 12.99, "Netflix")
        assertIncome("PayPal", "You've got money! John Smith sent you \$40.00 USD.", 40.0, "John Smith")
    }

    @Test
    fun usNonPaymentsAreIgnored() {
        listOf(
            "Your available balance is \$312.08 as of 10/22.",
            "Low balance alert: your checking balance is \$45.12.",
            "Your credit card payment of \$500.00 is due on 11/05.",
            "Your statement is ready. Statement balance: \$1,045.20.",
            "Your transaction of \$89.00 at BEST BUY was declined.",
            "Jane Doe requests \$15.00",
            "Get \$200 when you open a new checking account. Learn more.",
            "You earned \$10 cash back from Amex Offers.",
            "Your AutoPay of \$85.00 will be made on 11/03."
        ).forEach { text ->
            assertTrue(text, NotificationParser.parse("Bank", text, "USD") is ParseResult.Ignored)
        }
    }

    @Test
    fun thousandsWithoutCentsAreNotDecimals() {
        assertEquals(2500.0, recognized(null, "You spent \$2,500 at APPLE STORE", "USD").amount, 0.0)
        assertEquals(1234.0, recognized(null, "Direct deposit of \$1,234 posted", "USD").amount, 0.0)
    }
}
