# Политика конфиденциальности Bud-Jet

_Черновик. Перед публикацией проверьте формулировки, укажите контакт и дату, разместите по публичной ссылке и добавьте её в Play Console._

**Дата вступления в силу:** ____
**Контакт:** ____ (e-mail разработчика)

## Какие данные обрабатывает приложение

Bud-Jet — приложение для учёта личных расходов. Все финансовые записи (операции, категории, цели, лимиты, заметки) хранятся **только на вашем устройстве** в локальной базе данных приложения.

## Доступ к уведомлениям (автоучёт трат)

Функция «Автоучёт трат» необязательна: приложение полностью работает и с ручным вводом. Если вы включите её и выдадите доступ к уведомлениям в настройках Android:

- **Что читается.** Заголовок и текст уведомлений. Суммы, продавец и дата извлекаются только из уведомлений приложений, которые вы сами включили в списке «Приложения» (например, банк или платёжный сервис).
- **Другие приложения.** Их уведомления проверяются в памяти устройства только на наличие суммы с валютой, чтобы предложить вам добавить приложение в список. Из них сохраняется только название приложения и число таких уведомлений; текст не сохраняется.
- **Что хранится.** Распознанные траты сохраняются как обычные операции. Текст уведомлений, которые не удалось уверенно распознать, хранится на устройстве до тех пор, пока вы не подтвердите или не пропустите трату, но не дольше 30 дней.
- **Журнал событий.** Для проверки работы на устройстве хранятся последние 50 событий автоучёта (время, приложение, результат; текст — только для отслеживаемых приложений). Журнал можно очистить на экране «Автоучёт трат».
- **Что передаётся.** Текст уведомлений и полученные из них данные **не отправляются** на серверы разработчика или третьих лиц.
- **Как отключить.** Выключите доступ в разделе «Профиль → Автоучёт трат → Настройки доступа» или в системных настройках Android. Удалить все данные можно через «Профиль → Сбросить данные».

## Передача данных

Bud-Jet не продаёт ваши данные и не передаёт их для рекламы. Без ИИ-помощника приложение обращается в интернет только:

- за курсом валют при конвертации — передаются только коды валют;
- в Google Play — для оформления и проверки подписки.

Если ИИ-помощник выключен (так по умолчанию), ваши операции, заметки, цели и категории **никуда не отправляются**.

Резервные копии и экспорт создаются только по вашему действию и сохраняются туда, куда вы укажете.

Если на телефоне включено системное резервное копирование Android, Android может сохранять данные приложения (операции, категории, цели) в вашем аккаунте Google — это делает система, а не Bud-Jet, и управляется в настройках телефона. Журнал автоучёта в резервную копию не попадает.

## ИИ-помощник (тариф Pro)

ИИ-помощник выключен по умолчанию и доступен только в тарифе Pro. Он включается только после вашего явного согласия в приложении.

- **Когда отправляются данные.** Только когда вы сами запрашиваете советы или задаёте вопрос на экране «ИИ-помощник».
- **Что отправляется.** Сводка за последние три месяца: суммы доходов и расходов по месяцам, суммы по категориям (названия категорий), лимиты и траты по ним в текущем месяце, цель накоплений (сумма, накоплено, срок), валюта. Для вопросов — текст вашего вопроса и несколько предыдущих сообщений этого разговора.
- **Что не отправляется никогда.** Отдельные операции, заметки, тексты уведомлений, названия магазинов, данные банка и карт.
- **Служебные данные запроса.** Случайный идентификатор установки (не связан с вашей личностью; нужен для ограничения числа запросов), язык и версия приложения, токен покупки Google Play (нужен, чтобы сервер проверил подписку Pro).
- **Кто обрабатывает.** Сервер Bud-Jet передаёт сводку языковой модели (сервис Ollama) только для формирования ответа. Сводки и вопросы не хранятся после ответа и не используются для обучения моделей. _(Проверьте условия выбранного провайдера модели перед публикацией.)_
- **Как отключить.** Выключите «ИИ-помощник» в профиле — после этого ничего не отправляется. Разговор с помощником в приложении не сохраняется и пропадает при закрытии экрана.

Ответы ИИ могут быть неточными и не являются финансовой консультацией.

## Подписка

Есть два тарифа: «Базовый» (автоучёт трат и экспорт) и Pro (всё из «Базового» и ИИ-помощник). Оплату обрабатывает Google Play. Bud-Jet не получает данные вашей карты; приложение получает от Google Play только статус подписки и токен покупки. Чтобы подтвердить подписку, приложение может отправить токен покупки на сервер Bud-Jet, который проверяет его в Google Play.

## Дети

Приложение не предназначено для детей младше 13 лет.

## Изменения политики

Об изменениях сообщается обновлением этой страницы с новой датой вступления в силу.

---

# Bud-Jet Privacy Policy (English)

_Draft — review, add contact details and effective date, publish at a public URL and link it in Play Console._

Bud-Jet is a personal expense tracker. All finance records (transactions, categories, goals, limits, notes) are stored **only on your device**.

**Automatic tracking (optional).** If you enable it and grant notification access in Android settings, Bud-Jet reads the title and text of notifications. Amounts, merchant and date are extracted only from apps you turn on in the "Apps" list. Notifications from other apps are checked in memory only for an amount with a currency, to suggest adding the app; only the app name and a count are stored, never the text. Notifications that could not be recognized confidently are stored on the device until you confirm or skip them, for at most 30 days. Notification content and data derived from it are **never sent** to the developer or third parties. You can turn access off at any time in Profile → Automatic tracking or in Android settings.

Bud-Jet does not sell your data or share it for advertising. **Without the AI assistant** (it is off by default), your transactions, notes, goals and categories are never sent anywhere; the app goes online only to fetch currency exchange rates (currency codes only) and to talk to Google Play about your subscription. Backups and exports are created only on your request, in the location you choose.

**AI assistant (Pro plan, optional).** Off by default; it works only after you turn it on and agree. Data is sent only when you ask for tips or ask a question on the AI assistant screen: a summary of the last three months (income and expense totals per month, totals per category with category names, limits and this month's spending on them, your savings goal and currency), and for questions, the question text and a few previous messages of that conversation. Never sent: individual transactions, notes, notification texts, store names, bank or card details. Each request also carries a random install ID (not linked to you; used for rate limiting), app language and version, and your Google Play purchase token (to verify the Pro subscription). The Bud-Jet server passes the summary to a language model (Ollama) only to produce the answer; summaries and questions are not stored after answering and are not used for model training. Turn the assistant off in Profile and nothing is sent again. The conversation is not saved on the device. AI answers can be wrong and are not financial advice.

**Subscriptions.** Two plans: Basic (automatic tracking, export) and Pro (Basic plus the AI assistant). Payments are processed by Google Play; Bud-Jet never receives your card details, only the subscription status and a purchase token, which may be sent to the Bud-Jet server to verify the subscription with Google Play.
