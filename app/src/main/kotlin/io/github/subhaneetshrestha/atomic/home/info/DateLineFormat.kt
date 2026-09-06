package io.github.subhaneetshrestha.atomic.home.info

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Text of the date line: a theme pattern when valid, else the locale's default. Never throws. */
object DateLineFormat {
    fun format(
        date: LocalDate,
        locale: Locale,
        pattern: String?,
        defaultPattern: (Locale) -> String,
    ): String {
        val custom =
            pattern
                ?.takeIf {
                    it.isNotBlank()
                }?.let { runCatching { DateTimeFormatter.ofPattern(it, locale) }.getOrNull() }
        val fallback = { date.format(DateTimeFormatter.ofPattern(defaultPattern(locale), locale)) }
        return custom?.let { runCatching { date.format(it) }.getOrNull() } ?: fallback()
    }
}
