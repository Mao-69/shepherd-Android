package com.shepherd.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shepherd.ble.ConnState
import com.shepherd.session.SessionPhase
import com.shepherd.session.Tier

@Composable
fun ShepherdApp(vm: MainViewModel) {
    val dark by vm.darkTheme.collectAsState()
    ShepherdTheme(dark) {
        val pal = Palette(dark)
        val profile by vm.profile.collectAsState()
        val screen by vm.screen.collectAsState()
        val bg by vm.background.collectAsState()

        AppBackground(pal, bg) {
            when {
                !profile.onboarded -> OnboardingScreen(vm, pal)
                screen == Screen.SESSION -> SessionScreen(vm, pal)
                screen == Screen.SCRIPT_EDITOR -> ScriptEditorScreen(vm, pal)
                else -> MainShell(vm, pal, dark)
            }
        }
    }
}

/* ===================== ONBOARDING ===================== */

@Composable
private fun OnboardingScreen(vm: MainViewModel, pal: Palette) {
    var name by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }
    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(horizontal = 26.dp).padding(top = 90.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShepherdLogo(size = 88.dp, accent = pal.accent, ring = pal.indigo)
        Spacer(Modifier.height(22.dp))
        Text("SHEPHERD", color = pal.text, fontSize = 32.sp, fontWeight = FontWeight.Black,
            letterSpacing = 4.sp)
        Text("Guided remote viewing", color = pal.mid, fontSize = 14.sp)
        Spacer(Modifier.height(36.dp))

        when (step) {
            0 -> GlassCard(pal, strong = true) {
                Text("Welcome", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Shepherd guides you through structured remote-viewing sessions with " +
                    "binaural induction audio, spoken stage guidance, blinded targets, and " +
                    "optional live biometrics from a watch.",
                    color = pal.mid, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text("It's a personal research and meditation tool — not a medical device.",
                    color = pal.dim, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                GlassButton("Get started", pal) { step = 1 }
            }
            1 -> GlassCard(pal, strong = true) {
                Text("What should we call you?", color = pal.text, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("A display name for your sessions. A private viewer ID is generated " +
                    "automatically.", color = pal.mid, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(name, { name = it },
                    placeholder = { Text("Display name", color = pal.dim) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(pal))
                Spacer(Modifier.height(16.dp))
                GlassButton("Create profile", pal) { vm.completeOnboarding(name) }
            }
        }
    }
}

/* ===================== MAIN SHELL (tabs) ===================== */

@Composable
private fun MainShell(vm: MainViewModel, pal: Palette, dark: Boolean) {
    val tab by vm.currentTab.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // Scrolling content fills the whole area; top bar and bottom nav float over it.
        val scroll = rememberScrollState()

        // Top bar: transparent at rest, fades to opaque within the first ~40dp of scroll.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val topAlpha by remember {
            derivedStateOf {
                val px = with(density) { 40.dp.toPx() }
                (scroll.value / px).coerceIn(0f, 1f)
            }
        }
        // Bottom nav: opaque while there's content below (not yet at the end),
        // fades out as you reach the bottom so the last cards sit on glass.
        val bottomAlpha by remember {
            derivedStateOf {
                val remaining = scroll.maxValue - scroll.value
                val px = with(density) { 40.dp.toPx() }
                if (scroll.maxValue <= 0) 0f else (remaining / px).coerceIn(0f, 1f)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(start = 18.dp, end = 18.dp)
                // leave room so content scrolls UNDER the bars but isn't hidden at rest
                .padding(top = 96.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (tab) {
                Tab.SESSION -> SessionTab(vm, pal)
                Tab.TARGETS -> TargetsTab(vm, pal)
                Tab.MODES -> ModesTab(vm, pal)
                Tab.SETTINGS -> SettingsTab(vm, pal, dark)
            }
        }

        // Top bar — backing opacity tracks scroll
        Box(Modifier.align(Alignment.TopCenter)) {
            GlassTopBar(pal, scrolledAlpha = topAlpha,
                trailing = {
                    GlassIcon(Icons.Default.PlayArrow, pal) {
                        vm.selectTab(Tab.SESSION); vm.beginSession()
                    }
                })
        }

        // Bottom nav — backing opacity tracks remaining scroll
        Box(Modifier.align(Alignment.BottomCenter)) {
            GlassBottomNav(pal, tab, scrolledAlpha = bottomAlpha, onSelect = { vm.selectTab(it) }) { t ->
                when (t) {
                    Tab.SESSION -> Icons.Default.SelfImprovement
                    Tab.TARGETS -> Icons.Default.MyLocation
                    Tab.MODES -> Icons.Default.RecordVoiceOver
                    Tab.SETTINGS -> Icons.Default.Settings
                }
            }
        }
    }
}

/* ===================== SETUP ===================== */

/* ===================== TABS ===================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTab(vm: MainViewModel, pal: Palette) {
    var intention by remember { mutableStateOf(vm.intention) }
    var tier by remember { mutableStateOf(vm.tier) }
    var extended by remember { mutableStateOf(vm.extended) }
    var volume by remember { mutableStateOf(vm.volume.toFloat()) }
    var coordinate by remember { mutableStateOf(vm.coordinate) }

    GlassCard(pal) {
        CardTitle("Intention", pal)
        OutlinedTextField(
            value = intention, onValueChange = { intention = it; vm.intention = it },
            placeholder = { Text("One line — what is this session for?", color = pal.dim) },
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(pal)
        )
    }

    GlassCard(pal) {
        CardTitle("Protocol", pal)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tier.values().forEach { t ->
                Choice(t.label, t.approxMinutes, tier == t, pal, Modifier.weight(1f)) {
                    tier = t; vm.tier = t
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow("Extended journey (Focus 21–27)", extended, pal) {
            extended = it; vm.extended = it
        }
    }

    GlassCard(pal) {
        CardTitle("Coordinate", pal)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(coordinate, color = pal.accent, fontSize = 22.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f))
            GlassIcon(Icons.Default.Casino, pal) {
                vm.regenerateCoordinate(); coordinate = vm.coordinate
            }
        }
        Text("An SRI-style blind anchor, spoken during the viewing stages. " +
            "A pool or custom target overrides this.", color = pal.dim, fontSize = 12.sp)
    }

    GlassCard(pal) {
        CardTitle("Binaural audio", pal)
        Text("Volume  ${(volume * 100).toInt()}%", color = pal.mid, fontSize = 13.sp)
        Slider(value = volume, onValueChange = { volume = it; vm.volume = it.toDouble() },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent,
                inactiveTrackColor = pal.line))
        Text("Headphones required for the binaural effect.", color = pal.dim, fontSize = 12.sp)
    }

    GlassButton("BEGIN SESSION", pal, big = true) { vm.beginSession() }
    Text("CRV and binaural entrainment are unproven; this is a personal research tool, " +
        "not a medical device.", color = pal.dim, fontSize = 11.sp,
        modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun TargetsTab(vm: MainViewModel, pal: Palette) {
    GlassCard(pal) {
        CardTitle("Target pool", pal)
        TargetPoolBody(vm, pal)
    }
}

@Composable
private fun ModesTab(vm: MainViewModel, pal: Palette) {
    GlassCard(pal) {
        CardTitle("Guidance mode", pal)
        GuidanceModeBody(vm, pal)
    }
    GlassCard(pal) {
        CardTitle("Voice", pal)
        VoiceBody(vm, pal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(vm: MainViewModel, pal: Palette, dark: Boolean) {
    val profile by vm.profile.collectAsState()
    var name by remember { mutableStateOf(profile.name) }
    var useWatch by remember { mutableStateOf(vm.useWatch) }
    val tele by vm.telemetry.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val devices by vm.devices.collectAsState()

    GlassCard(pal) {
        CardTitle("Profile", pal)
        OutlinedTextField(name, { name = it; vm.updateName(it) },
            label = { Text("Display name", color = pal.dim) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(pal))
        Spacer(Modifier.height(8.dp))
        Text("Viewer ID: ${profile.userId}", color = pal.mid, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)
    }

    GlassCard(pal) {
        CardTitle("Appearance", pal)
        ToggleRow("Dark theme", dark, pal) { vm.setDark(it) }
    }

    GlassCard(pal) {
        CardTitle("Background", pal)
        BackgroundPicker(vm, pal)
    }

    GlassCard(pal) {
        CardTitle("Watch", pal)
        ToggleRow("Use watch for live biometrics", useWatch, pal) {
            useWatch = it; vm.useWatch = it
        }
        if (useWatch) {
            Spacer(Modifier.height(10.dp))
            WatchConnectBody(vm, pal, tele, scanning, devices)
        }
    }
}

@Composable
private fun TargetPoolBody(vm: MainViewModel, pal: Palette) {
    val fetch by vm.fetch.collectAsState()
    var usePool by remember { mutableStateOf(vm.useTargetPool) }
    val unused = remember(fetch.poolSize) { vm.pool.availableCoordinates().size }

    Column(Modifier.fillMaxWidth()) {
        Text("Blinded image targets from Wikimedia Commons. Build a pool, then draw " +
            "one at random — sealed until you reveal it after the session.",
            color = pal.mid, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        Text("Pool: ${fetch.poolSize} targets · $unused unused",
            color = pal.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (fetch.message.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(fetch.message, color = pal.dim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (fetch.busy) "Fetching…" else "Fetch 5", pal, Modifier.weight(1f)) {
                if (!fetch.busy) vm.fetchTargets(5)
            }
            SmallButton("Fetch 20", pal, Modifier.weight(1f)) {
                if (!fetch.busy) vm.fetchTargets(20)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("ADD YOUR OWN", color = pal.mid, fontSize = 11.sp, letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        var customTitle by remember { mutableStateOf("") }
        var customDesc by remember { mutableStateOf("") }
        var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
        val picker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri -> pickedUri = uri }

        OutlinedTextField(customTitle, { customTitle = it },
            placeholder = { Text("Target title (only you see it)", color = pal.dim) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(pal))
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(customDesc, { customDesc = it },
            placeholder = { Text("Optional notes for the reveal", color = pal.dim) },
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(pal))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (pickedUri != null) "Image selected ✓" else "Choose image", pal,
                Modifier.weight(1f)) { picker.launch("image/*") }
            SmallButton("Seal as target", pal, Modifier.weight(1f)) {
                pickedUri?.let { vm.importTargetImage(it, customTitle, customDesc)
                    customTitle = ""; customDesc = ""; pickedUri = null }
            }
        }
        Text("Your image is sealed locally and assigned a coordinate. Selecting it will " +
            "use it as the next session's target.", color = pal.dim, fontSize = 11.sp)
        if (fetch.poolSize > 0) {
            Spacer(Modifier.height(12.dp))
            ToggleRow("Draw a blind target from the pool", usePool, pal) {
                usePool = it; vm.useTargetPool = it
            }
            if (usePool) {
                Spacer(Modifier.height(6.dp))
                Text("The coordinate above will be replaced by a sealed pool draw at start.",
                    color = pal.dim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WatchConnectBody(vm: MainViewModel, pal: Palette, tele: com.shepherd.ble.Telemetry,
                             scanning: Boolean, devices: List<com.shepherd.ble.FoundDevice>) {
    Column(Modifier.fillMaxWidth()) {
        val connected = tele.connectionState == ConnState.READY
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape)
                .background(if (connected) pal.sage else pal.accent))
            Spacer(Modifier.width(8.dp))
            Text(
                when (tele.connectionState) {
                    ConnState.READY -> "Connected: ${tele.deviceName ?: "watch"}"
                    ConnState.CONNECTING -> "Pairing / connecting…"
                    ConnState.DISCOVERING -> "Discovering…"
                    ConnState.DISCONNECTED -> "Not connected"
                },
                color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            tele.battery?.let { Text("$it%", color = pal.mid, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(12.dp))
        if (!connected) {
            SmallButton(if (scanning) "Scanning…" else "Scan for watch", pal) {
                if (scanning) vm.stopScan() else vm.startScan()
            }
            devices.take(6).forEach { d ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(pal.hi).clickable { vm.connect(d.device) }
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Watch, null, tint = pal.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(d.name, color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${d.rssi}", color = pal.dim, fontSize = 12.sp)
                }
            }
        } else {
            SmallButton("Disconnect", pal) { vm.disconnectWatch() }
        }
    }
}

/* ===================== SESSION ===================== */

@Composable
private fun SessionScreen(vm: MainViewModel, pal: Palette) {
    val s by vm.sessionUi.collectAsState()
    val tele by vm.telemetry.collectAsState()
    var noteText by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    if (s.phase == SessionPhase.FINISHED) {
        // Finished panel is its own full scrollable screen.
        Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 52.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShepherdLogo(size = 34.dp, accent = pal.accent, ring = pal.indigo)
                Spacer(Modifier.width(10.dp))
                Text("SESSION", color = pal.accent, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            FinishedPanel(vm, pal, s)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val barAlpha by remember {
            derivedStateOf {
                val remaining = scroll.maxValue - scroll.value
                val px = with(density) { 40.dp.toPx() }
                if (scroll.maxValue <= 0) 0f else (remaining / px).coerceIn(0f, 1f)
            }
        }
        // Scrollable content — everything except the pinned transport bar.
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(start = 22.dp, end = 22.dp, top = 52.dp, bottom = 96.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShepherdLogo(size = 34.dp, accent = pal.accent, ring = pal.indigo)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.focusName.uppercase(), color = pal.accent, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Stage ${s.stageIndex + 1} of ${s.stageCount}",
                        color = pal.mid, fontSize = 12.sp)
                }
                IconChip(Icons.Default.Close, pal) { vm.endSession() }
            }

            Spacer(Modifier.height(18.dp))
            StageCard(pal, s)
            Spacer(Modifier.height(14.dp))

            if (tele.connectionState == ConnState.READY) {
                BioStrip(pal, tele, s)
                Spacer(Modifier.height(14.dp))
            }

            // Coordinate + status
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(pal.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MyLocation, null, tint = pal.indigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(s.coordinate, color = pal.text, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(s.statusMessage, color = pal.dim, fontSize = 11.sp)
            }

            Spacer(Modifier.height(14.dp))

            // Notes — fixed height (not weight), so it never crowds the controls.
            OutlinedTextField(
                value = noteText, onValueChange = { noteText = it },
                placeholder = { Text("Impressions for this stage…", color = pal.dim) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 240.dp),
                colors = fieldColors(pal)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Save note", pal, Modifier.weight(1f)) {
                    if (noteText.isNotBlank()) {
                        vm.session.appendNote(s.stage?.code ?: "", noteText)
                        noteText = ""
                    }
                }
                if (tele.connectionState == ConnState.READY) {
                    SmallButton("Re-measure", pal, Modifier.weight(1f)) { vm.session.remeasureVitals() }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Pinned transport controls — always reachable; backing fades in as content scrolls under.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            pal.bg.copy(alpha = 0f),
                            pal.bg.copy(alpha = 0.85f * barAlpha),
                            pal.bg.copy(alpha = 0.95f * barAlpha)
                        )
                    )
                )
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TransportButton(
                    if (s.phase == SessionPhase.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                    if (s.phase == SessionPhase.PAUSED) "Resume" else "Pause", pal, Modifier.weight(1f)
                ) { vm.session.togglePause() }
                TransportButton(Icons.Default.SkipNext, "Next stage", pal, Modifier.weight(1f)) {
                    vm.session.advanceStage()
                }
            }
        }
    }
}

@Composable
private fun StageCard(pal: Palette, s: com.shepherd.session.SessionUi) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(pal.card, pal.hi)))
        .border(1.dp, pal.line, RoundedCornerShape(22.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(pal,
                progress = if (s.stageDurationSec > 0)
                    s.stageElapsedSec.toFloat() / s.stageDurationSec else 0f,
                beat = s.beatHz)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(s.stage?.name ?: "—", color = pal.text, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(clock(s.stageElapsedSec) + " / " + clock(s.stageDurationSec),
                    color = pal.mid, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                if (s.beatHz > 0) Text("${s.beatHz.toInt()} Hz beat", color = pal.indigo, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(s.stageDescription.ifBlank { s.stage?.description ?: "" },
            color = pal.mid, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun ProgressRing(pal: Palette, progress: Float, beat: Double) {
    val infinite = rememberInfiniteTransition(label = "breathe")
    val pulse by infinite.animateFloat(0.92f, 1.0f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse")
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(82.dp)) {
            val sw = 7f
            drawArc(color = pal.line, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round))
            drawArc(color = pal.accent, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
        Box(Modifier.size((40 * pulse).dp).clip(CircleShape)
            .background(pal.accent.copy(alpha = 0.18f)))
        Text("${(progress * 100).toInt()}%", color = pal.text, fontSize = 13.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BioStrip(pal: Palette, tele: com.shepherd.ble.Telemetry, s: com.shepherd.session.SessionUi) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BioCell(Modifier.weight(1f), Icons.Default.Favorite, tele.heartRate?.let { "$it" } ?: "—", "bpm", pal.clay, pal)
        BioCell(Modifier.weight(1f), Icons.Default.Air, tele.spo2?.let { "$it" } ?: "—", "%", pal.indigo, pal)
        BioCell(Modifier.weight(1f), Icons.Default.MonitorHeart,
            if (tele.systolic != null) "${tele.systolic}/${tele.diastolic}" else "—", "", pal.sage, pal)
    }
}

@Composable
private fun BioCell(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector,
                    value: String, unit: String, accent: Color, pal: Palette) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(pal.card)
        .border(1.dp, pal.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = pal.dim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun FinishedPanel(vm: MainViewModel, pal: Palette, s: com.shepherd.session.SessionUi) {
    val reveal by vm.reveal.collectAsState()
    val scroll = rememberScrollState()
    // Phases of the post-session flow:
    //  0 = summary, 1 = blinded ranking (commit), 2 = revealed
    var phase by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        ShepherdLogo(size = 64.dp, accent = pal.accent, ring = pal.indigo)
        Spacer(Modifier.height(14.dp))
        Text("Session complete", color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Total time ${clock(s.totalElapsedSec)}", color = pal.mid, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))

        when {
            // No pool/custom target — just show notes + new session.
            !reveal.available -> {
                NotesBox(vm, pal)
                Spacer(Modifier.height(16.dp))
                BigButton("NEW SESSION", pal) { vm.backToSetup() }
            }
            // Custom (self-chosen) target: you already know it — reveal directly, no ranking.
            reveal.isCustom && phase != 2 -> {
                Text("Your chosen target. Review your notes, then reveal the image to " +
                    "compare against your impressions.",
                    color = pal.mid, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(14.dp))
                NotesBox(vm, pal)
                Spacer(Modifier.height(16.dp))
                BigButton("REVEAL TARGET", pal) { vm.doReveal(); phase = 2 }
            }
            phase == 0 -> {
                Text("This was a blinded target. Before revealing it, your notes will be " +
                    "hashed (commit-then-reveal), then you'll rank candidates by how well " +
                    "they match your impressions.",
                    color = pal.mid, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(14.dp))
                NotesBox(vm, pal)
                Spacer(Modifier.height(16.dp))
                BigButton("LOCK NOTES & RANK", pal) { vm.prepareReveal(); phase = 1 }
            }
            phase == 1 -> {
                Text("Blinded ranking", color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("One of these is your target; the rest are decoys. Note which feels " +
                    "closest to your impressions, then reveal.",
                    color = pal.mid, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(8.dp))
                reveal.decoys.forEachIndexed { i, (_, label) ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp)).background(pal.card)
                        .border(1.dp, pal.line, RoundedCornerShape(12.dp)).padding(14.dp)) {
                        Text("${('A' + i)}.  $label", color = pal.text, fontSize = 13.sp,
                            lineHeight = 18.sp)
                    }
                }
                if (reveal.notesHashPreReveal.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Notes SHA-256: ${reveal.notesHashPreReveal.take(16)}…",
                        color = pal.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(16.dp))
                BigButton("REVEAL TARGET", pal) { vm.doReveal(); phase = 2 }
            }
            else -> {
                RevealView(reveal.revealed, pal)
                Spacer(Modifier.height(16.dp))
                BigButton("NEW SESSION", pal) { vm.backToSetup() }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Decode a possibly-large image file, downsampled to ~maxDim px, avoiding OOM nulls. */
private fun decodeSampled(path: String, maxDim: Int): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    val (w, h) = bounds.outWidth to bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (w / sample > maxDim || h / sample > maxDim) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}.getOrNull()

@Composable
private fun RevealView(target: com.shepherd.session.RevealedTarget?, pal: Palette) {
    if (target == null) {
        Text("Could not reveal target.", color = pal.clay); return
    }
    Text("Target revealed", color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp)
    Spacer(Modifier.height(10.dp))
    // Decode asynchronously and keyed on the file path. produceState re-runs when the
    // target/path changes and recomposes when the bitmap is ready, so the first reveal
    // doesn't depend on the just-written file being decodable within the same frame.
    var decodeDone by remember(target.imageFile?.path) { mutableStateOf(false) }
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, key1 = target.imageFile?.path
    ) {
        val path = target.imageFile?.path
        value = if (path != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var out = decodeSampled(path, 1600)
            var tries = 0
            while (out == null && tries < 3) {
                kotlinx.coroutines.delay(120); out = decodeSampled(path, 1600); tries++
            }
            out
        } else null
        decodeDone = true
    }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp!!, contentDescription = target.title,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
        )
        Spacer(Modifier.height(10.dp))
    } else if (target.hasImage) {
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(16.dp))
            .background(pal.card), contentAlignment = Alignment.Center) {
            if (!decodeDone) {
                CircularProgressIndicator(color = pal.accent, strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp))
            } else {
                Text("Image couldn't be displayed", color = pal.dim, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    // (no image attached → show nothing, this is a text-only target)
    Text(target.title, color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(target.description, color = pal.mid, fontSize = 13.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(8.dp))
    Text("${target.source} · ${target.license}\n${target.attribution}",
        color = pal.dim, fontSize = 11.sp)
}

@Composable
private fun NotesBox(vm: MainViewModel, pal: Palette) {
    val notes = remember { vm.session.exportNotes() }
    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp)
        .clip(RoundedCornerShape(14.dp)).background(pal.card).padding(14.dp)) {
        val sc = rememberScrollState()
        Text(notes, color = pal.mid, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.verticalScroll(sc))
    }
}

/* ===================== shared bits ===================== */

@Composable
private fun VoiceBody(vm: MainViewModel, pal: Palette) {
    val voices by vm.voices.collectAsState()
    val selected by vm.selectedVoiceId.collectAsState()
    val rate by vm.voiceRate.collectAsState()
    val pitch by vm.voicePitch.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        if (voices.isEmpty()) {
            Text("Loading installed voices…", color = pal.mid, fontSize = 13.sp)
        } else {
            Text("Choose the guiding voice. Voices tagged Neural are the most natural " +
                "(some need a network connection); Enhanced are high-quality on-device.",
                color = pal.mid, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(12.dp))
            voices.take(12).forEach { v ->
                val isSel = v.id == selected
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) pal.accent.copy(alpha = 0.15f) else pal.hi)
                    .border(1.dp, if (isSel) pal.accent else pal.line, RoundedCornerShape(12.dp))
                    .clickable { vm.selectVoice(v.id) }
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(v.label, color = if (isSel) pal.accent else pal.text,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            if (v.neural) { Spacer(Modifier.width(8.dp)); Tag("NEURAL", pal.indigo, pal) }
                            else if (v.enhanced) { Spacer(Modifier.width(8.dp)); Tag("ENHANCED", pal.sage, pal) }
                        }
                        if (v.needsNetwork)
                            Text("needs network", color = pal.dim, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.PlayArrow, "Preview", tint = pal.accent,
                        modifier = Modifier.size(22.dp).clickable { vm.previewVoice(v.id) })
                }
            }
            if (voices.size <= 1) {
                Spacer(Modifier.height(8.dp))
                Text("Only one voice found. Install Google's neural voices for the most " +
                    "natural sound: Android Settings → Accessibility → Text-to-speech → " +
                    "Google → install voice data, then reopen Shepherd.",
                    color = pal.dim, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Speed  ${"%.2f".format(rate)}×", color = pal.mid, fontSize = 12.sp)
        Slider(value = rate, onValueChange = { vm.setVoiceRate(it) }, valueRange = 0.6f..1.3f,
            colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent,
                inactiveTrackColor = pal.line))
        Text("Pitch  ${"%.2f".format(pitch)}", color = pal.mid, fontSize = 12.sp)
        Slider(value = pitch, onValueChange = { vm.setVoicePitch(it) }, valueRange = 0.7f..1.3f,
            colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent,
                inactiveTrackColor = pal.line))
    }
}

@Composable
private fun Tag(text: String, color: Color, pal: Palette) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.2f))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun GuidanceModeBody(vm: MainViewModel, pal: Palette) {
    val scripts by vm.scripts.collectAsState()
    var selectedId by remember { mutableStateOf(vm.selectedScriptId) }

    Column(Modifier.fillMaxWidth()) {
        Text("What is voiced during each stage. Modes range from passive observation " +
            "to active application; you can also write and save your own.",
            color = pal.mid, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        scripts.forEach { sc ->
            val selected = sc.id == selectedId
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) pal.accent.copy(alpha = 0.15f) else pal.hi)
                .border(1.dp, if (selected) pal.accent else pal.line, RoundedCornerShape(12.dp))
                .clickable { selectedId = sc.id; vm.selectedScriptId = sc.id }
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sc.name, color = if (selected) pal.accent else pal.text,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(sc.description, color = pal.dim, fontSize = 11.sp, lineHeight = 15.sp)
                }
                if (!sc.builtIn) {
                    Icon(Icons.Default.Close, "Delete",
                        tint = pal.dim, modifier = Modifier.size(18.dp).clickable { vm.deleteScript(sc.id) })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SmallButton("Create / edit a custom mode", pal, Modifier.fillMaxWidth()) {
            vm.screen.value = Screen.SCRIPT_EDITOR
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundPicker(vm: MainViewModel, pal: Palette) {
    val current by vm.background.collectAsState()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.importBackground(uri) }

    Text("Glass cards float over this. Pick a built-in design or your own image.",
        color = pal.mid, fontSize = 13.sp, lineHeight = 18.sp)
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Backgrounds.builtins.forEach { kind ->
            BgTile(kind.key, kind.label, current == kind.key, pal) { vm.selectBackground(kind.key) }
        }
        // Custom tile
        val isCustom = current.startsWith("custom:")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(width = 70.dp, height = 110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Glass.fill(pal))
                    .border(if (isCustom) 2.dp else 1.dp,
                        if (isCustom) pal.accent else Glass.border(pal), RoundedCornerShape(12.dp))
                    .clickable { picker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AddPhotoAlternate, "Custom", tint = pal.accent,
                    modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("Gallery", color = if (isCustom) pal.accent else pal.mid, fontSize = 10.sp,
                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun BgTile(key: String, label: String, selected: Boolean, pal: Palette, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 70.dp, height = 110.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(if (selected) 2.dp else 1.dp,
                    if (selected) pal.accent else Glass.border(pal), RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
        ) {
            // Miniature of each design
            AppBackground(pal, key) {}
        }
        Spacer(Modifier.height(4.dp))
        Text(label.substringBefore(" ("), color = if (selected) pal.accent else pal.mid,
            fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1)
    }
}

@Composable
private fun CardTitle(t: String, pal: Palette) {
    Text(t.uppercase(), color = pal.mid, fontSize = 12.sp, letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun GlassButton(label: String, pal: Palette, big: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.horizontalGradient(listOf(pal.accent, Brand.LanternHi)))
        .clickable(onClick = onClick)
        .padding(vertical = if (big) 18.dp else 14.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = pal.bg, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
            fontSize = if (big) 15.sp else 14.sp)
    }
}

@Composable
private fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, pal: Palette, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
        .background(Glass.fill(pal)).border(1.dp, Glass.border(pal), RoundedCornerShape(12.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = pal.accent, modifier = Modifier.size(20.dp))
    }
}

@Composable private fun SectionTitle(t: String, pal: Palette) =
    Text(t.uppercase(), color = pal.mid, fontSize = 12.sp, letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))

@Composable
private fun ThemeToggle(dark: Boolean, onChange: (Boolean) -> Unit) {
    val pal = Palette(dark)
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(pal.card)
        .border(1.dp, pal.line, RoundedCornerShape(12.dp))
        .clickable { onChange(!dark) }, contentAlignment = Alignment.Center) {
        Icon(if (dark) Icons.Default.DarkMode else Icons.Default.LightMode, "Toggle theme",
            tint = pal.accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Choice(label: String, sub: String, selected: Boolean, pal: Palette,
                   modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp))
        .background(if (selected) pal.accent.copy(alpha = 0.16f) else pal.card)
        .border(1.dp, if (selected) pal.accent else pal.line, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (selected) pal.accent else pal.text,
            fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(sub, color = pal.dim, fontSize = 11.sp)
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, pal: Palette, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.card)
        .border(1.dp, pal.line, RoundedCornerShape(12.dp))
        .clickable { onChange(!value) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = pal.accent, checkedTrackColor = pal.accent.copy(alpha = 0.4f),
                uncheckedThumbColor = pal.dim, uncheckedTrackColor = pal.hi))
    }
}

@Composable
private fun BigButton(label: String, pal: Palette, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.horizontalGradient(listOf(pal.accent, Brand.LanternHi)))
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 17.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = pal.bg, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun SmallButton(label: String, pal: Palette, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(pal.hi)
        .border(1.dp, pal.line, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = pal.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun TransportButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                            pal: Palette, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(pal.card)
        .border(1.dp, pal.line, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = pal.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = pal.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun IconChip(icon: androidx.compose.ui.graphics.vector.ImageVector, pal: Palette, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(pal.card)
        .border(1.dp, pal.line, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = pal.accent, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors(pal: Palette) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = pal.accent, unfocusedBorderColor = pal.line,
    focusedTextColor = pal.text, unfocusedTextColor = pal.text, cursorColor = pal.accent,
)

private fun clock(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

/* ===================== SCRIPT EDITOR ===================== */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptEditorScreen(vm: MainViewModel, pal: Palette) {
    val scroll = rememberScrollState()
    // Stage codes the user can write text for (the meaningful guided stages).
    val editableStages = remember {
        listOf(
            "induction" to "Induction (Focus 10)",
            "stage1" to "Stage I — Ideogram",
            "stage2" to "Stage II — Sensory",
            "stage3" to "Stage III — Dimensions",
            "stage4" to "Stage IV — Aesthetic / AOL",
            "stage5" to "Stage V — Probing",
            "stage6" to "Stage VI — Modeling",
            "cooldown" to "Cool-down",
        )
    }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val texts = remember { mutableStateMapOf<String, String>() }

    Column(Modifier.fillMaxSize().verticalScroll(scroll)
        .padding(start = 22.dp, end = 22.dp, top = 56.dp, bottom = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Default.Close, pal) { vm.screen.value = Screen.SETUP }
            Spacer(Modifier.width(12.dp))
            Text("Custom guidance mode", color = pal.text, fontSize = 20.sp,
                fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(18.dp))

        // Optionally seed from an existing script
        val scripts by vm.scripts.collectAsState()
        Text("Start from", color = pal.mid, fontSize = 12.sp, letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scripts.forEach { sc ->
                SmallButton(sc.name, pal) {
                    name = sc.name + " (copy)"
                    desc = sc.description
                    texts.clear()
                    sc.perStage.forEach { (k, v) -> texts[k] = v }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(name, { name = it },
            label = { Text("Mode name", color = pal.dim) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(pal))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(desc, { desc = it },
            label = { Text("Short description", color = pal.dim) },
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(pal))

        Spacer(Modifier.height(18.dp))
        Text("Per-stage text (spoken in place of the default). Leave any blank to keep " +
            "the built-in wording for that stage.", color = pal.mid, fontSize = 13.sp,
            lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))

        editableStages.forEach { (code, label) ->
            Spacer(Modifier.height(10.dp))
            Text(label, color = pal.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = texts[code] ?: "",
                onValueChange = { texts[code] = it },
                placeholder = { Text("Spoken text for this stage…", color = pal.dim) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(pal),
                minLines = 2,
            )
        }

        Spacer(Modifier.height(20.dp))
        BigButton("SAVE MODE", pal) {
            val cleaned = texts.filterValues { it.isNotBlank() }
            vm.saveScript(name, desc, cleaned)
            vm.screen.value = Screen.SETUP
        }
        Spacer(Modifier.height(8.dp))
        Text("Saved modes appear in the Guidance list and can be tied to a session.",
            color = pal.dim, fontSize = 11.sp)
    }
}
