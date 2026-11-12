package ua.any2b.tvplayer.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import ua.any2b.tvplayer.R
import java.util.Locale

/** Track picker styled after the language and subtitle chips used on release cards. */
internal class PlayerTrackSelectionMenu(private val activity: Activity) {
    private var player: Player? = null
    private var controls: PlayerControlsView? = null
    private var audioButton: View? = null
    private var subtitleButton: View? = null
    private var audioGroups: List<Tracks.Group> = emptyList()
    private var subtitleGroups: List<Tracks.Group> = emptyList()

    fun bind(player: Player, controls: PlayerControlsView) {
        this.player = player
        this.controls = controls
        audioButton = controls.audioTrackButton
        subtitleButton = controls.subtitleTrackButton
        audioButton?.setOnClickListener { showAudioMenu() }
        subtitleButton?.setOnClickListener { showSubtitleMenu() }
        update(player.currentTracks)
    }

    fun update(tracks: Tracks) {
        audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .filter { group -> (0 until group.length).any(group::isTrackSupported) }
        subtitleGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .filter { group -> (0 until group.length).any(group::isTrackSupported) }
        val audioLanguageCount = languageCount(audioGroups)
        val subtitleLanguageCount = languageCount(subtitleGroups)
        audioButton?.isEnabled = audioLanguageCount > 1
        subtitleButton?.isEnabled = subtitleLanguageCount > 1
        controls?.audioTrackChoice?.text = choiceLabel(audioGroups, audioLanguageCount, C.TRACK_TYPE_AUDIO)
        controls?.subtitleTrackChoice?.text = choiceLabel(subtitleGroups, subtitleLanguageCount, C.TRACK_TYPE_TEXT)
        audioButton?.contentDescription = "Аудіодоріжка: ${controls?.audioTrackChoice?.text}"
        subtitleButton?.contentDescription = "Субтитри: ${controls?.subtitleTrackChoice?.text}"
        if (controls?.hasTrackActionFocus() == true &&
            audioButton?.isEnabled != true && subtitleButton?.isEnabled != true) {
            controls?.collapseTrackActions()
        }
    }

    private fun languageCount(groups: List<Tracks.Group>): Int = groups.flatMap { group ->
        (0 until group.length).filter(group::isTrackSupported)
            .map { index -> languageKey(group.getTrackFormat(index)) }
    }.distinct().size

    private fun languageKey(format: Format): String = when (format.language?.lowercase(Locale.ROOT)
        ?.substringBefore('-')) {
        "uk", "ukr" -> "uk"
        "en", "eng" -> "en"
        "ru", "rus" -> "ru"
        "ja", "jpn" -> "ja"
        "pl", "pol" -> "pl"
        else -> format.language?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() && it != "und" }
            ?: format.label?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
            ?: "und"
    }

    private fun choiceLabel(groups: List<Tracks.Group>, languageCount: Int, type: Int): String {
        if (languageCount == 0) return "—"
        if (languageCount == 1) return "Авто"
        if (type == C.TRACK_TYPE_TEXT &&
            type in (player?.trackSelectionParameters?.disabledTrackTypes ?: emptySet())) return "Вимк."
        val selected = groups.firstNotNullOfOrNull { group ->
            (0 until group.length).firstOrNull(group::isTrackSelected)
                ?.let(group::getTrackFormat)
        } ?: return "Авто"
        return shortLanguageName(selected)
    }

    private fun shortLanguageName(format: Format): String {
        val language = format.language?.lowercase(Locale.ROOT)?.substringBefore('-')
        return when (language) {
            "uk", "ukr" -> "Укр"
            "en", "eng" -> "Eng"
            "ru", "rus" -> "Рус"
            "ja", "jpn" -> "Яп"
            "pl", "pol" -> "Пол"
            "fr", "fra", "fre" -> "Фр"
            "de", "deu", "ger" -> "Нім"
            "es", "spa" -> "Ісп"
            "it", "ita" -> "Італ"
            "ko", "kor" -> "Кор"
            "zh", "zho", "chi" -> "Кит"
            else -> format.language?.takeIf { it.isNotBlank() && it != "und" }
                ?: format.label?.takeIf(String::isNotBlank)?.take(12)
                ?: "Авто"
        }
    }

    private fun showAudioMenu() {
        val groups = audioGroups
        if (groups.isEmpty()) return
        showMenu("АУДІОДОРІЖКА", groups, C.TRACK_TYPE_AUDIO, allowDisabled = false)
    }

    private fun showSubtitleMenu() {
        val groups = subtitleGroups
        if (groups.isEmpty()) return
        showMenu("СУБТИТРИ", groups, C.TRACK_TYPE_TEXT, allowDisabled = true)
    }

    private fun showMenu(title: String, groups: List<Tracks.Group>, type: Int, allowDisabled: Boolean) {
        val activePlayer = player ?: return
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(14))
            background = rounded(0xF21A1B25.toInt(), 14, 0xFF1CDDE2.toInt(), 1)
        }
        root.addView(TextView(activity).apply {
            text = title
            setTextColor(0xFF80F5FF.toInt())
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.06f
            setPadding(dp(2), 0, 0, dp(12))
        })
        val scroll = ScrollView(activity)
        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val parameters = activePlayer.trackSelectionParameters
        val autoSelected = parameters.overrides.keys.none { it.type == type } &&
            type !in parameters.disabledTrackTypes
        rows.addView(menuRow(
            title = "Автоматично",
            chips = listOf("AUTO" to 0xFF8793A5.toInt()),
            detail = "Вибір плеєра",
            selected = autoSelected,
        ) {
            activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(type)
                .setTrackTypeDisabled(type, false)
                .build()
            dialog.dismiss()
        })

        if (allowDisabled) {
            val selected = type in parameters.disabledTrackTypes
            rows.addView(menuRow(
                title = "Вимкнено",
                chips = listOf("CC" to 0xFF1CDDE2.toInt()),
                detail = "Без субтитрів",
                selected = selected,
            ) {
                val parameters = activePlayer.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(type)
                    .setTrackTypeDisabled(type, true)
                    .build()
                activePlayer.trackSelectionParameters = parameters
                dialog.dismiss()
            })
        }

        groups.forEach { group ->
            val supportedIndices = (0 until group.length).filter(group::isTrackSupported)
            if (supportedIndices.isEmpty()) return@forEach
            val format = group.getTrackFormat(supportedIndices.first())
            val selected = supportedIndices.any(group::isTrackSelected)
            val chips = if (type == C.TRACK_TYPE_AUDIO) {
                listOf("${FaIcons.MICROPHONE}" to 0xFF1CDDE2.toInt(), languageName(format) to 0xFF1CDDE2.toInt()) +
                    listOfNotNull(channelLabel(format)?.let { it to 0xFF8793A5.toInt() })
            } else {
                listOf("${FaIcons.COMMENTING_O}" to 0xFF1CDDE2.toInt(), languageName(format) to 0xFF1CDDE2.toInt()) +
                    listOfNotNull(subtitleFormat(format)?.let { it to 0xFF8793A5.toInt() }) + subtitleFlags(format)
            }
            val detail = format.label?.takeIf(String::isNotBlank)
                ?: codecName(format)
                ?: if (type == C.TRACK_TYPE_AUDIO) "Аудіодоріжка" else "Субтитри"
            rows.addView(menuRow(detail, chips, if (format.label != null) codecName(format) else null, selected) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, supportedIndices)
                activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(type)
                    .setTrackTypeDisabled(type, false)
                    .addOverride(override)
                    .build()
                dialog.dismiss()
            })
        }

        dialog.setContentView(root)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout((activity.resources.displayMetrics.widthPixels * 0.72f).toInt(),
                (activity.resources.displayMetrics.heightPixels * 0.76f).toInt())
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.68f }
        }
        dialog.setOnDismissListener {
            (if (type == C.TRACK_TYPE_AUDIO) audioButton else subtitleButton)?.requestFocus()
        }
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.72f).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.76f).toInt())
        rows.getChildAt(0)?.requestFocus()
    }

    private fun menuRow(
        title: String,
        chips: List<Pair<String, Int>>,
        detail: String?,
        selected: Boolean,
        onClick: () -> Unit,
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(dp(12), dp(9), dp(12), dp(9))
        background = rounded(if (selected) 0x4420DDE2 else 0x33202B3A,
            9, if (selected) 0xFF1CDDE2.toInt() else 0x6655DDE0, 1)
        val spacing = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            chips.forEach { (label, tint) -> addView(chip(label, tint)) }
            addView(TextView(activity).apply {
                text = title
                setTextColor(0xFFF3F4F8.toInt())
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
            if (selected) addView(TextView(activity).apply {
                text = "✓"
                setTextColor(0xFF80F5FF.toInt())
                textSize = 18f
                setPadding(dp(8), 0, 0, 0)
            })
        })
        if (!detail.isNullOrBlank() && detail != title) addView(TextView(activity).apply {
            text = detail
            setTextColor(0xFFAAB2C0.toInt())
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), dp(5), 0, 0)
        })
        setOnFocusChangeListener { view, focused ->
            view.background = rounded(if (focused) 0xFF1CDDE2.toInt() else if (selected) 0x4420DDE2 else 0x33202B3A,
                9, if (focused || selected) 0xFF80F5FF.toInt() else 0x6655DDE0, if (focused) 2 else 1)
            if (focused) setChildTextColor(this, 0xFF071117.toInt())
            else setChildTextColor(this, 0xFFF3F4F8.toInt())
        }
        setOnClickListener { onClick() }
        layoutParams = spacing
    }

    private fun setChildTextColor(view: LinearLayout, color: Int) {
        fun walk(parent: android.view.ViewGroup) {
            for (index in 0 until parent.childCount) {
                when (val child = parent.getChildAt(index)) {
                    is TextView -> if (child.text != "✓") child.setTextColor(color)
                    is android.view.ViewGroup -> walk(child)
                }
            }
        }
        walk(view)
    }

    private fun chip(label: String, tint: Int): View = TextView(activity).apply {
        text = label
        if (label.firstOrNull()?.code?.let { it in 0xF000..0xF8FF } == true) {
            typeface = activity.resources.getFont(R.font.fa_solid_900)
            textSize = 12f
        } else {
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        setTextColor(0xFFF3F4F8.toInt())
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background = rounded(if (tint == 0xFF1CDDE2.toInt()) 0x4439DCE0 else 0x333C485A,
            6, tint, 0)
        val params = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(5) }
        layoutParams = params
    }

    private fun languageName(format: Format): String {
        val tag = format.language?.takeIf { it.isNotBlank() && it != "und" } ?: return "Мова?"
        val normalized = when (tag.lowercase(Locale.ROOT)) {
            "uk", "ukr" -> "uk"
            "en", "eng" -> "en"
            "ru", "rus" -> "ru"
            "ja", "jpn" -> "ja"
            "fr", "fra", "fre" -> "fr"
            "de", "deu", "ger" -> "de"
            "es", "spa" -> "es"
            "it", "ita" -> "it"
            "pl", "pol" -> "pl"
            "ko", "kor" -> "ko"
            "zh", "zho", "chi" -> "zh"
            else -> tag
        }
        return Locale.forLanguageTag(normalized).getDisplayLanguage(Locale("uk"))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("uk")) else it.toString() }
    }

    private fun channelLabel(format: Format): String? = when (format.channelCount) {
        1 -> "1.0"
        2 -> "2.0"
        3 -> "3.0"
        4 -> "4.0"
        5 -> "5.0"
        6 -> "5.1"
        7 -> "6.1"
        8 -> "7.1"
        else -> format.channelCount.takeIf { it > 0 }?.let { "$it ch" }
    }

    private fun codecName(format: Format): String? {
        val mime = format.sampleMimeType?.lowercase(Locale.ROOT) ?: return format.codecs
        return when {
            mime.contains("eac3") -> "E-AC-3"
            mime.contains("ac3") -> "AC-3"
            mime.contains("truehd") -> "TrueHD"
            mime.contains("dts") -> "DTS"
            mime.contains("aac") -> "AAC"
            mime.contains("flac") -> "FLAC"
            mime.contains("opus") -> "Opus"
            mime.contains("vorbis") -> "Vorbis"
            mime.contains("mpeg") -> "MP3"
            mime.contains("subrip") -> "SRT"
            mime.contains("vtt") -> "WebVTT"
            mime.contains("pgs") -> "PGS"
            mime.contains("ass") || mime.contains("ssa") -> "ASS/SSA"
            else -> format.codecs ?: mime.substringAfter('/')
        }
    }

    private fun subtitleFormat(format: Format): String? = codecName(format)

    private fun subtitleFlags(format: Format): List<Pair<String, Int>> = buildList {
        if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0)
            add("Форсовані" to 0xFFD6B6FF.toInt())
        if (format.roleFlags and C.ROLE_FLAG_CAPTION != 0)
            add("SDH" to 0xFF8793A5.toInt())
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
