package ua.any2b.core.toloka

import java.io.IOException

data class TolokaHit(
    val id: String,
    val title: String,
    val pageUrl: String,
    val sizeText: String,
    val seeders: Int?,
    val leechers: Int?,
    val author: String? = null,
)

data class TolokaSearch(
    val query: String,
    val results: List<TolokaHit>,
    val cached: Boolean,
    val sessionReused: Boolean?,
)

sealed class TolokaFailure(message: String) : IOException(message) {
    class RateLimited(val waitSeconds: Long) : TolokaFailure("Toloka тимчасово обмежила запити")
    class LinkExpired : TolokaFailure("Посилання на torrent протухло. Повторіть пошук Toloka")
    class Unauthorized : TolokaFailure("Toloka не підтвердила вхід. Перевірте логін і пароль у конфігу")
    class InvalidResponse : TolokaFailure("Toloka повернула неочікувані дані")
    class Http(val status: Int) : TolokaFailure("Toloka відповіла HTTP $status")
    class Timeout : TolokaFailure("Час очікування Toloka вичерпано")
    class Network : TolokaFailure("Не вдалося з’єднатися з Toloka")
}
