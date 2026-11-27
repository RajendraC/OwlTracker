package com.owl.tracker.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A savings goal: a jar the user fills by noting what they've set aside. The money itself stays in their bank;
 * the jar only tracks it. [by] is optional.
 */
data class Goal(val id: String, val name: String, val emoji: String, val targetPaise: Long, val savedPaise: Long, val by: LocalDate?) {
    val fraction: Float get() = if (targetPaise > 0) (savedPaise.toFloat() / targetPaise).coerceIn(0f, 1f) else 0f
    val done: Boolean get() = savedPaise >= targetPaise && targetPaise > 0

    /** What's needed each month from [today] to reach it by [by] (null without a date, or once done). */
    fun perMonth(today: LocalDate): Long? {
        val end = by ?: return null
        if (done) return null
        val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), end.withDayOfMonth(1)).coerceAtLeast(1)
        return (targetPaise - savedPaise + months - 1) / months
    }
}

object Goals {
    const val MAX = 12
    val PICTURES = listOf("🏖️", "📱", "🚗", "🏠", "🎓", "💍", "✈️", "🛵", "💻", "🎁", "🩺", "🐷")

    fun fromJson(raw: String?): List<Goal> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until minOf(a.length(), MAX)).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").filter { it.isLetterOrDigit() || it == '-' }.take(40).ifEmpty { return@mapNotNull null }
            val name = o.optString("name").filter { !it.isISOControl() }.trim().take(40).ifEmpty { return@mapNotNull null }
            val emoji = o.optString("emoji").takeIf { it in PICTURES } ?: "🐷"
            val target = o.optLong("target", 0).coerceIn(0, 1_000_000_000_00L)
            val saved = o.optLong("saved", 0).coerceIn(0, 1_000_000_000_00L)
            val by = o.optString("by").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (target <= 0) null else Goal(id, name, emoji, target, saved, by)
        }
    }.getOrDefault(emptyList())

    fun toJson(goals: List<Goal>): String = JSONArray().apply {
        goals.take(MAX).forEach { g ->
            put(JSONObject().put("id", g.id).put("name", g.name).put("emoji", g.emoji).put("target", g.targetPaise).put("saved", g.savedPaise).apply { g.by?.let { put("by", it.toString()) } })
        }
    }.toString()
}
