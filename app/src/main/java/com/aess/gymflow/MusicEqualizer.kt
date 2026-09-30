package com.aess.gymflow

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class EqualizerUiState(
    val supported: Boolean = false,
    val sessionId: Int = 0,
    val enabled: Boolean = false,
    val frequencies: List<Int> = emptyList(),
    val levels: List<Int> = emptyList(),
    val minimum: Int = 0,
    val maximum: Int = 0,
    val presets: List<String> = emptyList()
)

/** Owned by MusicService; never attaches to the deprecated global audio mix. */
internal class MusicEqualizer(context: Context, private val state: MutableStateFlow<EqualizerUiState>) {
    private val prefs = context.getSharedPreferences("gymflow_equalizer", Context.MODE_PRIVATE)
    private var effect: Equalizer? = null
    private var sessionId = 0

    fun attach(id: Int) {
        if (id == sessionId && effect != null) return
        release()
        sessionId = id
        if (id <= 0) return
        runCatching {
            val eq = Equalizer(0, id)
            effect = eq
            val range = eq.bandLevelRange
            require(range.size >= 2 && range[0] < range[1] && eq.numberOfBands > 0)
            val frequencies = (0 until eq.numberOfBands.toInt()).map { eq.getCenterFreq(it.toShort()) / 1000 }
            frequencies.forEachIndexed { band, hz -> eq.setBandLevel(band.toShort(), prefs.getInt("band_$hz", 0).coerceIn(range[0].toInt(), range[1].toInt()).toShort()) }
            check(eq.setEnabled(prefs.getBoolean("enabled", false)) == AudioEffect.SUCCESS)
            state.value = EqualizerUiState(true, id, eq.enabled, frequencies,
                frequencies.indices.map { eq.getBandLevel(it.toShort()).toInt() }, range[0].toInt(), range[1].toInt(),
                runCatching { (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) } }.getOrDefault(emptyList()))
        }.onFailure { unavailable() }
    }

    fun enabled(value: Boolean) = operate {
        check(it.setEnabled(value) == AudioEffect.SUCCESS)
        state.value = state.value.copy(enabled = it.enabled)
        save()
    }

    fun band(index: Int, value: Int) = operate {
        if (index !in state.value.levels.indices) return@operate
        val safe = value.coerceIn(state.value.minimum, state.value.maximum)
        it.setBandLevel(index.toShort(), safe.toShort())
        state.value = state.value.copy(levels = state.value.levels.toMutableList().also { levels -> levels[index] = safe })
    }

    fun flat() = operate {
        state.value.levels.indices.forEach { band -> it.setBandLevel(band.toShort(), 0.coerceIn(state.value.minimum, state.value.maximum).toShort()) }
        refreshLevels(it)
        save()
    }

    fun preset(index: Int) = operate {
        if (index !in state.value.presets.indices) return@operate
        it.usePreset(index.toShort())
        refreshLevels(it)
        save()
    }

    fun save() {
        if (!state.value.supported) return
        val current = state.value
        prefs.edit().putBoolean("enabled", current.enabled).apply {
            current.frequencies.forEachIndexed { index, hz -> putInt("band_$hz", current.levels[index]) }
        }.apply()
    }

    private fun refreshLevels(eq: Equalizer) {
        state.value = state.value.copy(levels = state.value.frequencies.indices.map { eq.getBandLevel(it.toShort()).toInt() })
    }

    private fun operate(action: (Equalizer)->Unit) {
        val eq = effect ?: return
        runCatching { action(eq) }.onFailure { unavailable() }
    }

    private fun unavailable() {
        val id = sessionId
        release()
        state.value = EqualizerUiState(sessionId = id)
    }

    fun release() {
        runCatching { save() }
        runCatching { effect?.release() }
        effect = null
        sessionId = 0
        state.value = EqualizerUiState()
    }
}

@Composable internal fun EqualizerDialog(language: String, onClose: ()->Unit) {
    val state by MusicService.equalizerState.collectAsState()
    AlertDialog(onDismissRequest = onClose,
        title = { Text(if (language == "EN") "Equalizer" else "Эквалайзер") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.supported) {
                    Text(if (language == "EN") "No supported equalizer is available for this audio session. Start playback or try another output device." else "Для этой аудиосессии эквалайзер недоступен. Запустите воспроизведение или попробуйте другое устройство вывода.")
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Text(if (language == "EN") "On / Off" else "Вкл. / Выкл.", Modifier.weight(1f))
                        Switch(state.enabled, { MusicService.equalizer?.enabled(it) })
                    }
                    TextButton(onClick = { MusicService.equalizer?.flat() }) { Text("Flat") }
                    var presetsOpen by remember { mutableStateOf(false) }
                    if (state.presets.isNotEmpty()) Box {
                        TextButton(onClick = { presetsOpen = true }) { Text(if (language == "EN") "Presets" else "Пресеты") }
                        DropdownMenu(presetsOpen, { presetsOpen = false }) {
                            state.presets.forEachIndexed { index, name -> DropdownMenuItem(text = { Text(name) }, onClick = { MusicService.equalizer?.preset(index); presetsOpen = false }) }
                        }
                    }
                    state.frequencies.forEachIndexed { index, hz ->
                        Text("$hz Hz · ${state.levels[index] / 100f} dB")
                        Slider(state.levels[index].toFloat(), { MusicService.equalizer?.band(index, it.toInt()) },
                            onValueChangeFinished = { runCatching { MusicService.equalizer?.save() } },
                            valueRange = state.minimum.toFloat()..state.maximum.toFloat(), enabled = state.enabled)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(gs(language, R.string.close)) } }
    )
}
