package com.shepherd.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shepherd.audio.BinauralEngine
import com.shepherd.audio.VoiceEngine
import com.shepherd.ble.BleScanner
import com.shepherd.ble.P58Manager
import com.shepherd.session.SessionEngine
import com.shepherd.session.Tier
import com.shepherd.session.TargetPool
import com.shepherd.session.WikimediaSource
import com.shepherd.session.RevealedTarget
import com.shepherd.session.FetchedTarget
import com.shepherd.session.GuidanceScript
import com.shepherd.session.GuidanceScripts
import com.shepherd.session.ScriptLibrary
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Application.dataStore by preferencesDataStore("shepherd_prefs")
private val DARK_KEY = booleanPreferencesKey("dark_theme")
private val VOICE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("voice_id")
private val RATE_KEY = androidx.datastore.preferences.core.floatPreferencesKey("voice_rate")
private val PITCH_KEY = androidx.datastore.preferences.core.floatPreferencesKey("voice_pitch")
private val NAME_KEY = androidx.datastore.preferences.core.stringPreferencesKey("profile_name")
private val UID_KEY = androidx.datastore.preferences.core.stringPreferencesKey("profile_uid")
private val ONBOARDED_KEY = booleanPreferencesKey("onboarded")
private val BG_KEY = androidx.datastore.preferences.core.stringPreferencesKey("background")

enum class Screen { SETUP, SESSION, SCRIPT_EDITOR }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val scanner = BleScanner(app)
    val watch = P58Manager(app)
    val audio = BinauralEngine()
    val voice = VoiceEngine(app)
    val session = SessionEngine(viewModelScope, audio, voice, watch)

    val devices = scanner.devices
    val scanning = scanner.scanning
    val telemetry = watch.state
    val sessionUi = session.ui

    // Theme persistence
    val darkTheme = app.dataStore.data
        .map { it[DARK_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setDark(dark: Boolean) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[DARK_KEY] = dark }
    }

    // --- Voice selection ---
    private val _voices = MutableStateFlow<List<VoiceEngine.VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceEngine.VoiceOption>> = _voices.asStateFlow()
    private val _selectedVoiceId = MutableStateFlow<String?>(null)
    val selectedVoiceId: StateFlow<String?> = _selectedVoiceId.asStateFlow()
    val voiceRate = MutableStateFlow(0.9f)
    val voicePitch = MutableStateFlow(0.95f)

    init {
        // Once TTS is ready, load saved prefs and populate the voice list.
        voice.setOnReady {
            viewModelScope.launch {
                val prefs = getApplication<Application>().dataStore.data.first()
                prefs[RATE_KEY]?.let { voiceRate.value = it }
                prefs[PITCH_KEY]?.let { voicePitch.value = it }
                voice.setRatePitch(voiceRate.value, voicePitch.value)
                val savedVoice = prefs[VOICE_KEY]
                if (savedVoice != null) voice.selectVoice(savedVoice)
                _voices.value = voice.availableVoices()
                _selectedVoiceId.value = voice.currentVoiceId()
            }
        }
    }

    fun selectVoice(id: String) {
        voice.selectVoice(id)
        _selectedVoiceId.value = voice.currentVoiceId()
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[VOICE_KEY] = id }
        }
    }

    fun previewVoice(id: String) = voice.preview(id)

    fun setVoiceRate(r: Float) {
        voiceRate.value = r
        voice.setRatePitch(r, voicePitch.value)
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[RATE_KEY] = r } }
    }

    fun setVoicePitch(p: Float) {
        voicePitch.value = p
        voice.setRatePitch(voiceRate.value, p)
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[PITCH_KEY] = p } }
    }

    fun refreshVoices() { _voices.value = voice.availableVoices() }

    val screen = MutableStateFlow(Screen.SETUP)

    // --- Profile & onboarding ---
    val profile = MutableStateFlow(com.shepherd.session.Profile("", "", onboarded = false))
    val currentTab = MutableStateFlow(Tab.SESSION)

    init {
        viewModelScope.launch {
            val p = getApplication<Application>().dataStore.data.first()
            val onboarded = p[ONBOARDED_KEY] ?: false
            profile.value = com.shepherd.session.Profile(
                name = p[NAME_KEY] ?: "",
                userId = p[UID_KEY] ?: "",
                onboarded = onboarded,
            )
            p[BG_KEY]?.let { background.value = it }
        }
    }

    fun completeOnboarding(name: String) {
        val uid = com.shepherd.session.ProfileGen.newUserId()
        profile.value = com.shepherd.session.Profile(name.ifBlank { "Viewer" }, uid, onboarded = true)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit {
                it[NAME_KEY] = profile.value.name
                it[UID_KEY] = uid
                it[ONBOARDED_KEY] = true
            }
        }
    }

    fun updateName(name: String) {
        profile.value = profile.value.copy(name = name)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[NAME_KEY] = name }
        }
    }

    fun selectTab(tab: Tab) { currentTab.value = tab }

    // --- Background selection ---
    val background = MutableStateFlow(com.shepherd.ui.BgKind.GRADIENT.key)

    fun selectBackground(key: String) {
        background.value = key
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[BG_KEY] = key }
        }
    }

    /** Copy a picked image into app storage and use it as the custom background. */
    fun importBackground(uri: android.net.Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(getApplication<Application>().filesDir, "bg").apply { mkdirs() }
                    val out = java.io.File(dir, "custom_bg.jpg")
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { ins ->
                        out.outputStream().use { ins.copyTo(it) }
                    }
                    out.absolutePath
                }.getOrNull()
            }
            if (path != null) selectBackground("custom:$path")
        }
    }

    // --- Target pool (blinded image targets) ---
    val pool = TargetPool(app)
    private val source = WikimediaSource()

    // --- Guidance scripts (modes) ---
    val scriptLibrary = ScriptLibrary(app)
    private val _scripts = MutableStateFlow(scriptLibrary.all())
    val scripts: StateFlow<List<GuidanceScript>> = _scripts.asStateFlow()
    var selectedScriptId: String = GuidanceScripts.DEFAULT.id

    fun refreshScripts() { _scripts.value = scriptLibrary.all() }

    fun saveScript(name: String, description: String, perStage: Map<String, String>,
                   existingId: String? = null) {
        val id = existingId ?: scriptLibrary.newId()
        scriptLibrary.save(GuidanceScript(id, name.ifBlank { "Untitled script" },
            description, builtIn = false, perStage = perStage))
        refreshScripts()
        selectedScriptId = id
    }

    fun deleteScript(id: String) {
        scriptLibrary.delete(id); refreshScripts()
        if (selectedScriptId == id) selectedScriptId = GuidanceScripts.DEFAULT.id
    }

    data class FetchStatus(val busy: Boolean = false, val message: String = "", val poolSize: Int = 0)
    private val _fetch = MutableStateFlow(FetchStatus(poolSize = pool.size()))
    val fetch: StateFlow<FetchStatus> = _fetch.asStateFlow()

    /** When true, the session draws a blind coordinate from the pool and a reveal is possible. */
    var useTargetPool: Boolean = false
    private var drawnFromPool: Boolean = false

    // Commit-then-reveal + ranking state surfaced to the finished screen
    data class RevealState(
        val available: Boolean = false,           // target came from the pool
        val isCustom: Boolean = false,            // user's own target → skip blind ranking
        val notesHashPreReveal: String = "",
        val decoys: List<Pair<String, String>> = emptyList(), // (coord, "title — desc") shown blinded
        val realCoord: String = "",
        val revealed: RevealedTarget? = null,
    )
    private val _reveal = MutableStateFlow(RevealState())
    val reveal: StateFlow<RevealState> = _reveal.asStateFlow()

    /** Import a user-supplied image as a sealed target. Returns assigned coordinate (async via flow). */
    fun importTargetImage(uri: Uri, title: String, description: String) {
        if (_fetch.value.busy) return
        viewModelScope.launch {
            _fetch.value = _fetch.value.copy(busy = true, message = "Importing image…")
            val result = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                val t = FetchedTarget(
                    title = title.ifBlank { "My target" },
                    description = description.ifBlank { "User-supplied target." },
                    imageUrl = null, imageBytes = bytes?.takeIf { it.isNotEmpty() },
                    source = "User import", license = "—", attribution = "User", fileTitle = null,
                )
                val c = pool.add(t)
                c to (bytes?.size ?: 0)
            }
            val (coord, size) = result
            _fetch.value = FetchStatus(busy = false,
                message = if (size > 0) "Imported target $coord (${size / 1024} KB image)"
                          else "Imported $coord — WARNING: no image data read",
                poolSize = pool.size())
            useTargetPool = true
            coordinate = coord
            directCustomCoord = coord
        }
    }

    /** When set, beginSession uses this specific pool coordinate instead of a random draw. */
    var directCustomCoord: String? = null

    fun fetchTargets(count: Int) {
        if (_fetch.value.busy) return
        viewModelScope.launch {
            _fetch.value = FetchStatus(busy = true, message = "Fetching…", poolSize = pool.size())
            var added = 0; var attempts = 0
            withContext(Dispatchers.IO) {
                while (added < count && attempts < count * 4) {
                    attempts++
                    val t = source.fetchOne()
                    if (t == null) {
                        _fetch.value = _fetch.value.copy(message = "Retry $attempts…")
                        Thread.sleep(1500); continue
                    }
                    pool.add(t)
                    added++
                    _fetch.value = _fetch.value.copy(
                        message = "Added $added/$count", poolSize = pool.size())
                    Thread.sleep(1200) // be polite to Wikimedia
                }
            }
            _fetch.value = FetchStatus(busy = false,
                message = "Pool: ${pool.size()} targets (${pool.availableCoordinates().size} unused)",
                poolSize = pool.size())
        }
    }

    // --- Setup config (held until session start) ---
    var tier: Tier = Tier.STANDARD
    var extended: Boolean = false
    var useWatch: Boolean = true
    var intention: String = ""
    var coordinate: String = com.shepherd.session.Protocol.generateCoordinate()
    var volume: Double = 0.35
    var coordIntervalSec: Int = 180

    fun regenerateCoordinate() { coordinate = com.shepherd.session.Protocol.generateCoordinate() }

    fun startScan() = scanner.start()
    fun stopScan() = scanner.stop()
    fun connect(device: BluetoothDevice) { scanner.stop(); watch.connect(device) }
    fun disconnectWatch() = watch.disconnect()

    fun beginSession() {
        // Priority: a directly-chosen custom target, else a blind pool draw, else the coordinate as-is.
        drawnFromPool = false
        var custom = false
        val direct = directCustomCoord
        if (direct != null && pool.hasEntry(direct) && !pool.isRevealed(direct)) {
            coordinate = direct; drawnFromPool = true; custom = true
        } else if (useTargetPool) {
            val picked = pool.pickRandom()
            if (picked != null) { coordinate = picked; drawnFromPool = true }
        }
        _reveal.value = RevealState(available = drawnFromPool, isCustom = custom, realCoord = coordinate)
        val script = scriptLibrary.byId(selectedScriptId)
        screen.value = Screen.SESSION
        session.start(
            tier = tier, extended = extended,
            coordinate = coordinate, intention = intention,
            initialVolume = volume, coordIntervalSec = coordIntervalSec,
            script = script,
        )
    }

    /** Commit half of commit-then-reveal: hash the notes BEFORE showing the target. */
    fun prepareReveal() {
        if (!drawnFromPool) return
        val target = _reveal.value.realCoord
        val notes = session.exportNotes().toByteArray()
        val hash = com.shepherd.session.TargetCrypto.sha256Hex(notes)
        val decoyCoords = pool.pickDecoys(target, 3)
        val shown = (decoyCoords + target).shuffled().mapNotNull { c ->
            pool.peekForRanking(c)?.let { (title, desc) ->
                c to "$title — ${desc.take(140)}"
            }
        }
        _reveal.value = _reveal.value.copy(
            available = true, notesHashPreReveal = hash, decoys = shown)
    }

    /** Reveal half: decrypt and surface the real target (off the main thread). */
    fun doReveal() {
        if (!drawnFromPool) return
        val coord = _reveal.value.realCoord
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { pool.reveal(coord) }
            _reveal.value = _reveal.value.copy(revealed = r)
        }
    }

    fun endSession() {
        session.abort()
        audio.stop()
    }

    fun backToSetup() {
        audio.stop()
        screen.value = Screen.SETUP
    }

    override fun onCleared() {
        session.abort()
        audio.stop()
        voice.shutdown()
        scanner.stop()
        watch.disconnect()
        super.onCleared()
    }
}
