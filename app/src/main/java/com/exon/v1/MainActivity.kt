package com.exon.v1

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Renkler ----------
val Bg = Color(0xFF0B0B14)
val Surf = Color(0xFF161625)
val Surf2 = Color(0xFF1F1F33)
val Violet = Color(0xFF7C5CFF)
val Cyan = Color(0xFF22D3EE)
val Green = Color(0xFF34D399)
val TextC = Color(0xFFF2F2FA)
val Muted = Color(0xFF8A8AA8)
val Grad = Brush.linearGradient(listOf(Violet, Cyan))

val AvatarColors = listOf(
    Violet to Cyan,
    Color(0xFFFF6B9D) to Color(0xFFFFB86B),
    Color(0xFF34D399) to Color(0xFF22D3EE),
    Color(0xFFF59E0B) to Color(0xFFEF4444),
    Color(0xFF60A5FA) to Color(0xFF8B5CF6),
    Color(0xFFEC4899) to Color(0xFF8B5CF6)
)

sealed class Screen {
    object Home : Screen()
    object EditProfile : Screen()
    data class Chat(val id: String) : Screen()
    data class PeerProfile(val id: String) : Screen()
}

class MainActivity : ComponentActivity() {
    private val vm: ExonViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.nearby.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { ExonApp(vm) }
        permLauncher.launch(neededPermissions())
    }

    private fun neededPermissions(): Array<String> {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            l += Manifest.permission.BLUETOOTH_ADVERTISE
            l += Manifest.permission.BLUETOOTH_CONNECT
            l += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= 33) {
            l += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return l.toTypedArray()
    }
}

// ---------- Kök ----------
@Composable
fun ExonApp(vm: ExonViewModel) {
    val me by vm.profile.collectAsStateWithLifecycle()
    val peerMap by vm.nearby.peers.collectAsStateWithLifecycle()
    val msgs by vm.nearby.messages.collectAsStateWithLifecycle()
    val active by vm.nearby.active.collectAsStateWithLifecycle()
    val error by vm.nearby.error.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    BackHandler(enabled = screen !is Screen.Home) {
        val s = screen
        screen = if (s is Screen.PeerProfile) Screen.Chat(s.id) else Screen.Home
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when (val s = screen) {
            is Screen.Home -> {
                val list = peerMap.values.sortedWith(
                    compareByDescending<Peer> { it.connected }.thenBy { it.name.lowercase() }
                )
                HomeScreen(
                    me = me,
                    peers = list,
                    active = active,
                    error = error,
                    last = { id -> msgs[id]?.lastOrNull()?.text },
                    onMe = { screen = Screen.EditProfile },
                    onPeer = { p ->
                        if (p.connected) screen = Screen.Chat(p.id)
                        else if (!p.connecting) vm.nearby.connect(p.id)
                    },
                    onPeerProfile = { p -> screen = Screen.PeerProfile(p.id) },
                    onRescan = { vm.nearby.restart() }
                )
            }
            is Screen.EditProfile -> ProfileEditScreen(
                initial = me,
                onBack = { screen = Screen.Home },
                onSave = { vm.saveProfile(it); screen = Screen.Home }
            )
            is Screen.Chat -> {
                val peer = peerMap[s.id]
                if (peer == null) {
                    LaunchedEffect(Unit) { screen = Screen.Home }
                } else {
                    ChatScreen(
                        peer = peer,
                        list = msgs[s.id] ?: emptyList(),
                        onBack = { screen = Screen.Home },
                        onProfile = { screen = Screen.PeerProfile(s.id) },
                        onSend = { vm.nearby.sendMessage(s.id, it) }
                    )
                }
            }
            is Screen.PeerProfile -> {
                val peer = peerMap[s.id]
                if (peer == null) {
                    LaunchedEffect(Unit) { screen = Screen.Home }
                } else {
                    PeerProfileScreen(
                        peer = peer,
                        onBack = { screen = Screen.Chat(s.id) },
                        onMessage = { screen = Screen.Chat(s.id) }
                    )
                }
            }
        }
    }
}

// ---------- Ortak parçalar ----------
@Composable
fun AppIcon(icon: ImageVector, tint: Color = TextC, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun Avatar(p: Profile?, fallback: String, size: Dp) {
    val (a, b) = AvatarColors[(p?.color ?: 0).coerceIn(0, AvatarColors.lastIndex)]
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center
    ) {
        if (p != null) {
            AppIcon(SvgIcons.avatars[p.icon.coerceIn(0, SvgIcons.avatars.lastIndex)], Color.White, size * 0.5f)
        } else {
            Text(
                fallback.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Surf).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) { AppIcon(SvgIcons.Back, size = 22.dp) }
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextC, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun GradientButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Grad else Brush.linearGradient(listOf(Surf2, Surf2)))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun exonFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Surf2,
    unfocusedContainerColor = Surf2,
    disabledContainerColor = Surf,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = TextC,
    unfocusedTextColor = TextC,
    disabledTextColor = Muted,
    cursorColor = Cyan
)

// ---------- Ana ekran ----------
@Composable
fun HomeScreen(
    me: Profile,
    peers: List<Peer>,
    active: Boolean,
    error: String?,
    last: (String) -> String?,
    onMe: () -> Unit,
    onPeer: (Peer) -> Unit,
    onPeerProfile: (Peer) -> Unit,
    onRescan: () -> Unit
) {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s"
    )

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Exon", style = TextStyle(brush = Grad, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Surf2).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("v1", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.clickable { onMe() }) { Avatar(me, me.name, 46.dp) }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(12.dp).scale(if (active) pulse else 1f)
                        .clip(CircleShape).background(if (active) Green else Muted)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (active) "Yakındaki cihazlar aranıyor…" else "Tarama kapalı",
                    color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )
                Text("Bluetooth + Wi-Fi · internet ve SIM gerekmez", color = Muted, fontSize = 12.sp)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Surf2).clickable { onRescan() },
                contentAlignment = Alignment.Center
            ) { AppIcon(SvgIcons.Radar, Cyan, 22.dp) }
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Color(0xFFFF7A8A), fontSize = 12.sp)
        }

        Spacer(Modifier.height(18.dp))
        Text("Yakındakiler", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (peers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Grad), contentAlignment = Alignment.Center) {
                    AppIcon(SvgIcons.Bolt, Color.White, 40.dp)
                }
                Spacer(Modifier.height(18.dp))
                Text("Henüz kimse yok", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Yanındaki kişinin de Exon v1'i açık olsun.\nBluetooth ve konum açık olmalı.",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(peers, key = { it.id }) { p ->
                    PeerRow(p, last(p.id), onClick = { onPeer(p) }, onAvatar = { onPeerProfile(p) })
                }
            }
        }
    }
}

@Composable
fun PeerRow(peer: Peer, last: String?, onClick: () -> Unit, onAvatar: () -> Unit) {
    val sub = last
        ?: peer.profile?.bio?.takeIf { it.isNotBlank() }
        ?: if (peer.connected) "Bağlı" else "Yakınında"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf)
            .clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clickable { onAvatar() }) { Avatar(peer.profile, peer.name, 52.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(peer.name, color = TextC, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        when {
            peer.connected -> AppIcon(SvgIcons.Chat, Cyan, 24.dp)
            peer.connecting -> Text("Bağlanıyor…", color = Muted, fontSize = 12.sp)
            else -> Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Grad).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Bağlan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// ---------- Sohbet ----------
@Composable
fun ChatScreen(
    peer: Peer,
    list: List<Msg>,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(list.size) {
        if (list.isNotEmpty()) state.animateScrollToItem(list.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(Surf).statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Surf2).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { AppIcon(SvgIcons.Back, size = 22.dp) }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).clickable { onProfile() }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(peer.profile, peer.name, 42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(peer.name, color = TextC, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (peer.connected) "Bağlı" else "Bağlantı koptu",
                        color = if (peer.connected) Green else Muted, fontSize = 12.sp
                    )
                }
            }
        }

        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(list) { m ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start
                ) {
                    val shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (m.mine) 18.dp else 4.dp,
                        bottomEnd = if (m.mine) 4.dp else 18.dp
                    )
                    Column(
                        Modifier.widthIn(max = 280.dp).clip(shape)
                            .background(if (m.mine) Grad else Brush.linearGradient(listOf(Surf2, Surf2)))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(m.text, color = Color.White, fontSize = 15.sp)
                        Text(
                            fmt.format(Date(m.time)),
                            color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(if (peer.connected) "Mesaj yaz…" else "Bağlı değil", color = Muted) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = exonFieldColors(),
                enabled = peer.connected,
                maxLines = 4
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(50.dp).clip(CircleShape)
                    .background(if (peer.connected) Grad else Brush.linearGradient(listOf(Surf2, Surf2)))
                    .clickable(enabled = peer.connected && text.isNotBlank()) {
                        onSend(text); text = ""
                    },
                contentAlignment = Alignment.Center
            ) { AppIcon(SvgIcons.Send, Color.White, 22.dp) }
        }
    }
}

// ---------- Profil düzenleme ----------
@Composable
fun ProfileEditScreen(initial: Profile, onBack: () -> Unit, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var bio by remember { mutableStateOf(initial.bio) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    val preview = Profile(name, bio, icon, color)

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        TopBar("Profilim", onBack)
        Column(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))
            Avatar(preview, name, 110.dp)
            Spacer(Modifier.height(8.dp))
            Text(name.ifBlank { "İsimsiz" }, color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            Text("Avatar", color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SvgIcons.avatars.forEachIndexed { i, ic ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Surf2)
                            .then(if (i == icon) Modifier.border(2.dp, Cyan, CircleShape) else Modifier)
                            .clickable { icon = i },
                        contentAlignment = Alignment.Center
                    ) { AppIcon(ic, if (i == icon) Cyan else Muted, 22.dp) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Renk", color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarColors.forEachIndexed { i, (a, b) ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(a, b)))
                            .then(if (i == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickable { color = i },
                        contentAlignment = Alignment.Center
                    ) { if (i == color) AppIcon(SvgIcons.Check, Color.White, 20.dp) }
                }
            }

            Spacer(Modifier.height(20.dp))
            TextField(
                value = name, onValueChange = { if (it.length <= 24) name = it },
                label = { Text("İsim", color = Muted) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), colors = exonFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = bio, onValueChange = { if (it.length <= 80) bio = it },
                label = { Text("Hakkımda", color = Muted) },
                maxLines = 3, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), colors = exonFieldColors()
            )
        }
        Box(Modifier.padding(20.dp)) {
            GradientButton("Kaydet", enabled = name.isNotBlank()) {
                onSave(Profile(name.trim(), bio.trim(), icon, color))
            }
        }
    }
}

// ---------- Başkasının profili ----------
@Composable
fun PeerProfileScreen(peer: Peer, onBack: () -> Unit, onMessage: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("Profil", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(30.dp))
            Avatar(peer.profile, peer.name, 130.dp)
            Spacer(Modifier.height(16.dp))
            Text(peer.name, color = TextC, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Surf2).padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    if (peer.connected) "● Bağlı" else "○ Çevrimdışı",
                    color = if (peer.connected) Green else Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf).padding(18.dp)) {
                Text("Hakkında", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    peer.profile?.bio?.takeIf { it.isNotBlank() }
                        ?: if (peer.profile == null) "Profil bilgisi bağlanınca gelir." else "Henüz bir şey yazılmamış.",
                    color = TextC, fontSize = 15.sp
                )
            }
        }
        if (peer.connected) {
            Box(Modifier.padding(20.dp)) { GradientButton("Mesaj gönder", onClick = onMessage) }
        }
    }
}
