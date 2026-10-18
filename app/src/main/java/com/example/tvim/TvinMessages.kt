package com.example.tvim

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar
import org.json.JSONArray
import kotlin.random.Random

data class TvinMessage(val id: Int, val hour: Int, val minute: Int, val texts: List<String>) {
    constructor(id: Int, hour: Int, minute: Int, text: String) : this(id, hour, minute, listOf(text))
    val text: String get() = texts.firstOrNull().orEmpty()
}

class TvinMessages(private val context: Context) {
    private val prefs = context.getSharedPreferences("tvin_messages", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
            restore()
            if (!value) (0..1).forEach { NotificationManagerCompat.from(context).cancel("tvin", it) }
        }

    fun messages(): List<TvinMessage> = (0..1).map { id ->
        TvinMessage(id, prefs.getInt("hour_$id", if (id == 0) 8 else 22).coerceIn(0, 23),
            prefs.getInt("minute_$id", 0).coerceIn(0, 59),
            variants(id))
    }

    fun save(message: TvinMessage) {
        require(message.id in 0..1 && message.hour in 0..23 && message.minute in 0..59)
        require(message.texts.size in 0..30 && message.texts.all { it.isNotBlank() && it.length <= 300 })
        prefs.edit().putInt("hour_${message.id}", message.hour)
            .putInt("minute_${message.id}", message.minute)
            .putString("variants_${message.id}", JSONArray(message.texts.map { it.trim() }.distinct()).toString())
            .putBoolean("custom_only_${message.id}", true).remove("text_${message.id}").apply()
        schedule(message)
    }

    private fun variants(id: Int): List<String> {
        val saved = prefs.getString("variants_$id", null)
        val parsed = if (saved != null) runCatching {
            val array = JSONArray(saved)
            (0 until array.length()).map { array.getString(it).trim() }
                .filter { it.isNotBlank() && it.length <= 300 }.distinct().take(30)
        }.getOrDefault(emptyList()) else listOfNotNull(prefs.getString("text_$id", null)?.takeIf { it.isNotBlank() })
        if (prefs.getBoolean("custom_only_$id", false)) return parsed
        // One-time removal of shipped greetings; edited and user-created messages survive.
        val shipped = (legacyDefaultVariants(id, "ru") + legacyDefaultVariants(id, "en") +
            listOf("Доброе утро!", "Спокойной ночи!", "Good Morning!", "Good Night!")).toSet()
        val custom = parsed.filterNot { it in shipped }
        prefs.edit().putString("variants_$id", JSONArray(custom).toString())
            .putBoolean("custom_only_$id", true).remove("text_$id").apply()
        return custom
    }

    fun chooseText(message: TvinMessage, random: Random = Random.Default): String? {
        if (message.texts.isEmpty()) return null
        val previous = prefs.getString("last_${message.id}", null)
        val candidates = message.texts.filter { it != previous }.ifEmpty { message.texts }
        val selected = candidates.random(random)
        prefs.edit().putString("last_${message.id}", selected).apply()
        return selected
    }

    fun restore() = messages().forEach { schedule(it) }

    private fun intent(id: Int, at: Long = 0L) = Intent(context, TvinMessageReceiver::class.java)
        .setAction("com.example.tvim.TVIN_$id").putExtra("id", id).putExtra("at", at)

    private fun schedule(message: TvinMessage) {
        val pending = PendingIntent.getBroadcast(context, message.id, intent(message.id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { alarms.cancel(pending); pending.cancel() }
        prefs.edit().remove("scheduled_${message.id}").apply()
        if (!enabled || message.texts.isEmpty()) {
            NotificationManagerCompat.from(context).cancel("tvin", message.id)
            return
        }
        val at = nextTime(message.hour, message.minute, System.currentTimeMillis())
        val next = PendingIntent.getBroadcast(context, message.id, intent(message.id, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        prefs.edit().putLong("scheduled_${message.id}", at).apply()
        // Greetings are ordinary notifications; they do not need exact-alarm privileges.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, next)
    }

    fun deliver(id: Int, at: Long) {
        if (!enabled || id !in 0..1 || at <= 0L || prefs.getLong("scheduled_$id", 0L) != at) return
        val message = messages().first { it.id == id }
        prefs.edit().remove("scheduled_$id").apply()
        schedule(message)
        // Do not show yesterday's greeting after a long device sleep.
        if (System.currentTimeMillis() - at !in 0L..(2 * 60 * 60 * 1000L)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "TVIM", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (!notificationsAllowed()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = chooseText(message) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("TVIM")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build()
        try { manager.notify("tvin", id, notification) } catch (_: SecurityException) { /* Permission revoked. */ }
    }

    fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    companion object {
        const val CHANNEL = "tvin_messages"
        fun legacyDefaultVariants(id: Int, language: String): List<String> = if (language == "ru") {
            if (id == 0) listOf(
                "Доброе утро! Пусть сегодня найдётся повод улыбнуться.",
                "Новый день начинается. Дай себе немного времени проснуться.",
                "Доброе утро! Начни день в своём ритме.",
                "Пора просыпаться. Сегодня можно сделать что-то приятное для себя.",
                "Привет, новый день! Пусть утро будет спокойным.",
                "Доброе утро! Большие дела начинаются с маленького шага.",
                "Открой глаза, потянись — день уже ждёт.",
                "Пусть сегодня будет больше хороших встреч и добрых слов.",
                "Утро — время для нового начала. Не спеши, у тебя свой темп.",
                "Доброе утро! Найди сегодня время для того, что любишь."
            ) else listOf(
                "Спокойной ночи! Пусть сон будет тихим и тёплым.",
                "День позади. Можно отложить дела и отдохнуть.",
                "Пора замедлиться. Завтра будет новый день.",
                "Спокойной ночи! Оставь немного забот на завтра.",
                "Пусть эта ночь подарит тебе хороший отдых.",
                "Время для тишины и уютных снов.",
                "Ты многое сделал сегодня. Теперь можно передохнуть.",
                "Отложи телефон, устройся поудобнее. Доброй ночи!",
                "Пусть хорошие моменты этого дня останутся с тобой. Сладких снов!",
                "На сегодня хватит спешки. Спокойной ночи!"
            )
        } else {
            if (id == 0) listOf(
                "Good morning! May today give you a reason to smile.",
                "A new day is here. Give yourself a moment to wake up.",
                "Good morning! Start the day at your own pace.",
                "Time to wake up. Do something kind for yourself today.",
                "Hello, new day! May your morning be peaceful.",
                "Good morning! Big things begin with a small step.",
                "Open your eyes and stretch — the day is waiting.",
                "May today bring good company and kind words.",
                "Morning is a fresh start. Take it at your own pace.",
                "Good morning! Make time for something you love today."
            ) else listOf(
                "Good night! Wishing you peaceful, cozy sleep.",
                "The day is done. Put your tasks aside and rest.",
                "Time to slow down. Tomorrow is a new day.",
                "Good night! Leave a few worries for tomorrow.",
                "May tonight bring you a good rest.",
                "Time for quiet moments and cozy dreams.",
                "You have done a lot today. Now take a break.",
                "Put your phone down and get comfortable. Good night!",
                "Keep the good moments of today with you. Sweet dreams!",
                "Enough rushing for today. Sleep well!"
            )
        }
        fun nextTime(hour: Int, minute: Int, now: Long): Long = Calendar.getInstance().run {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            timeInMillis
        }
    }
}

class TvinMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TvinMessages(context).deliver(intent.getIntExtra("id", -1), intent.getLongExtra("at", 0))
    }
}
