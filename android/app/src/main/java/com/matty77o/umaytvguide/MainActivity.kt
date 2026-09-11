package com.matty77o.umaytvguide

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.max
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import androidx.core.content.ContextCompat

private const val GUIDE_URL =
    "https://raw.githubusercontent.com/Matty77o/tv-iptv/main/guide.xml"

private const val CHANNEL_CONFIG_URL =
    "https://raw.githubusercontent.com/Matty77o/tv-iptv/main/channels.json"

private const val AI_ADVISER_URL =
    "https://umay-tv-ai.matthewwood406.workers.dev/api/adviser"

private const val DUCKTV_LOGO_URL =
    "https://epg.ovh/logo/Duck+TV.png"

private fun channelKey(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

private fun resolvedChannelIcon(channel: TvChannel, config: ChannelConfig?): String? =
    config?.icon?.takeIf { it.isNotBlank() }
        ?: if (channelKey(channel.id) == "ducktv") DUCKTV_LOGO_URL else channel.icon


private fun ageToSliderPosition(ageMonths: Int): Float = ageMonths.coerceIn(0, 60).toFloat()

private fun sliderPositionToAge(position: Float): Int =
    kotlin.math.round(position.coerceIn(0f, 60f)).toInt()

private fun formatAge(ageMonths: Int): String {
    val months = ageMonths.coerceIn(0, 60)
    if (months < 12) return if (months == 1) "1 month" else "$months months"
    val years = months / 12
    val remainder = months % 12
    return when {
        remainder == 0 -> if (years == 1) "1 year" else "$years years"
        years == 1 -> "1 year $remainder ${if (remainder == 1) "month" else "months"}"
        else -> "$years years $remainder ${if (remainder == 1) "month" else "months"}"
    }
}

private const val PREFS_NAME = "umay_tv_guide"
private const val PREF_FAVOURITES = "favourite_show_titles"
private const val PREF_FAVOURITE_CHANNELS = "favourite_channels"
private const val PREF_REMINDER_MODE = "reminder_mode"
private const val PREF_SHOW_REMINDER_MODES = "show_reminder_modes"
private const val PREF_TIME_24 = "time_24_hour"
private const val PREF_AUTO_REFRESH = "auto_refresh"
private const val PREF_DEFAULT_SECTION = "default_section"
private const val PREF_HOUSEHOLD_CODE = "household_pairing_code"
private const val PREF_SCHEDULE_JSON = "shared_tv_schedule"
private const val PREF_HOUSEHOLD_MEMBER = "household_member_name"
private const val PREF_SCHEDULE_CHOOSE_NOW = "schedule_choose_now"

data class ChannelConfig(
    val id: String,
    val group: String,
    val order: Int,
    val name: String? = null,
    val icon: String? = null,
    val hidden: Boolean = false,
)

private val DefaultChannelConfig = listOf(
    ChannelConfig("BabyFirst", "Kids", 10),
    ChannelConfig("CBeebies", "Kids", 20),
    ChannelConfig("PBS KIDS", "Kids", 30),
    ChannelConfig("Moonbug Kids", "Kids", 40),
    ChannelConfig("Baby Shark TV", "Kids", 50),
    ChannelConfig("Duck TV", "Kids", 60, icon = DUCKTV_LOGO_URL),
    ChannelConfig("Kidoodle TV", "Kids", 65),
    ChannelConfig("TRT Çocuk", "Kids", 70),
    ChannelConfig("Minika Çocuk", "Kids", 80),
    ChannelConfig("TRT 1", "Turkish TV", 90),
    ChannelConfig("Star TV", "Turkish TV", 100),
    ChannelConfig("NOW", "Turkish TV", 110),
    ChannelConfig("ATV", "Turkish TV", 120),
    ChannelConfig("Show TV", "Turkish TV", 130),
)

data class TvChannel(
    val id: String,
    val name: String,
    val icon: String?,
    val group: String? = null,
)

data class Programme(
    val channelId: String,
    val start: ZonedDateTime,
    val stop: ZonedDateTime,
    val title: String,
    val description: String?,
    val category: String?,
    val icon: String? = null,
    val subtitle: String? = null,
    val episodeNumber: String? = null,
    val originalDate: String? = null,
    val rating: String? = null,
    val isNew: Boolean = false,
)

data class GuideData(
    val channels: List<TvChannel>,
    val programmes: List<Programme>,
)

data class ReminderOpenRequest(
    val title: String,
    val channelId: String,
    val startEpochMillis: Long,
)

enum class GuideFilter(val label: String) {
    ALL("All"),
    KIDS("Kids"),
    TURKISH("Turkish TV"),
    FAVOURITES("Favourite Shows")
}

enum class GuideJumpTarget { NOW, TONIGHT, TOMORROW }

enum class AppSection(val label: String) {
    HOME("Home"), GUIDE("Guide"), FAVOURITES("Favourites"), SCHEDULE("Schedule"), AI("Ask AI"), SETTINGS("Settings")
}

private fun AppSection.navLabel(): String = when (this) {
    AppSection.FAVOURITES -> "Faves"
    AppSection.AI -> "AI"
    else -> label
}

enum class ReminderMode(val label: String) {
    BOTH("10 min before + start"),
    TEN_MINUTES("10 min before only"),
    START("When it starts only"),
    OFF("Off")
}

class MainActivity : ComponentActivity() {
    private val reminderOpenRequest = mutableStateOf<ReminderOpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reminderOpenRequest.value = reminderRequestFromIntent(intent)

        setContent {
            UmayTheme {
                AppUpdateGate {
                    Surface(modifier = Modifier.fillMaxSize()) {
                    GuideScreen(
                        reminderOpenRequest = reminderOpenRequest.value,
                        onReminderConsumed = { reminderOpenRequest.value = null },
                    )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderOpenRequest.value = reminderRequestFromIntent(intent)
    }

    private fun reminderRequestFromIntent(intent: Intent?): ReminderOpenRequest? {
        intent ?: return null
        if (!intent.getBooleanExtra(ProgrammeReminderReceiver.EXTRA_OPEN_PROGRAMME, false)) {
            return null
        }

        val title = intent.getStringExtra(ProgrammeReminderReceiver.EXTRA_TITLE) ?: return null
        val channelId = intent.getStringExtra(ProgrammeReminderReceiver.EXTRA_CHANNEL).orEmpty()
        val startEpochMillis =
            intent.getLongExtra(ProgrammeReminderReceiver.EXTRA_START_EPOCH, -1L)

        return ReminderOpenRequest(
            title = title,
            channelId = channelId,
            startEpochMillis = startEpochMillis,
        )
    }
}

private fun isSamsungDevice(): Boolean =
    Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
        Build.BRAND.equals("samsung", ignoreCase = true)

private val Midnight = Color(0xFF040711)
private val Canvas = Color(0xFF060B16)
private val Panel = Color(0xFF09111F)
private val Panel2 = Color(0xFF0D182B)
private val Panel3 = Color(0xFF14233C)
private val SurfaceHigh = Color(0xFF111D33)
private val SurfaceSoft = Color(0xFF0A1425)
private val Pink = Color(0xFFFF4FA3)
private val PinkSoft = Color(0xFFFF9CCB)
private val Lavender = Color(0xFFA58CFF)
private val Cyan = Color(0xFF62D9FF)
private val Mint = Color(0xFF6CE6B7)
private val Amber = Color(0xFFFFC766)
private val TextPrimary = Color(0xFFF8FAFF)
private val TextSecondary = Color(0xFFAAB5CB)
private val TextTertiary = Color(0xFF73809B)
private val Hairline = Color.White.copy(alpha = .075f)
private val StrongHairline = Color.White.copy(alpha = .115f)

@Composable
fun UmayTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Pink,
        secondary = Lavender,
        tertiary = Cyan,
        background = Midnight,
        surface = Panel,
        surfaceVariant = Panel2,
        onPrimary = Color(0xFF25000F),
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(34.dp),
        ),
        content = content,
    )
}

private fun sectionIcon(section: AppSection) = when (section) {
    AppSection.HOME -> Icons.Rounded.Home
    AppSection.GUIDE -> Icons.Rounded.Tv
    AppSection.FAVOURITES -> Icons.Rounded.Favorite
    AppSection.SCHEDULE -> Icons.Rounded.CalendarMonth
    AppSection.AI -> Icons.Rounded.Star
    AppSection.SETTINGS -> Icons.Rounded.Settings
}

@Composable
private fun PremiumIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accent: Color = TextPrimary,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .065f), Color.White.copy(alpha = .025f))))
            .border(1.dp, StrongHairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = accent, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun PremiumTopBar(
    searchOpen: Boolean,
    searchQuery: String,
    onSearchQuery: (String) -> Unit,
    onCloseSearch: () -> Unit,
    lastUpdated: LocalTime?,
    use24Hour: Boolean,
    isLandscape: Boolean,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF050914), Midnight)))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        if (searchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQuery,
                singleLine = true,
                placeholder = { Text("Search programmes or channels", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
                trailingIcon = { IconButton(onClick = onCloseSearch) { Icon(Icons.Rounded.Close, "Close search") } },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceHigh.copy(alpha = .94f),
                    unfocusedContainerColor = SurfaceHigh.copy(alpha = .94f),
                    focusedBorderColor = Pink.copy(alpha = .48f),
                    unfocusedBorderColor = StrongHairline,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isLandscape) 7.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Umay", color = TextPrimary, fontSize = if (isLandscape) 20.sp else 25.sp, fontWeight = FontWeight.Black, letterSpacing = (-.8).sp)
                        Text(" TV", color = PinkSoft, fontSize = if (isLandscape) 20.sp else 25.sp, fontWeight = FontWeight.Black, letterSpacing = (-.8).sp)
                        Spacer(Modifier.width(9.dp))
                        Surface(
                            color = Pink.copy(alpha = .11f),
                            shape = RoundedCornerShape(100.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Pink.copy(alpha = .22f)),
                        ) {
                            Text("GUIDE", color = PinkSoft, fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = .7.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    if (!isLandscape) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(Mint, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                lastUpdated?.let { "Live guide • ${formatTime(it, use24Hour)}" } ?: "Live household guide",
                                color = TextSecondary,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                PremiumIconButton(Icons.Rounded.Search, "Search", onClick = onSearch)
                Spacer(Modifier.width(7.dp))
                PremiumIconButton(Icons.Rounded.Refresh, "Refresh", accent = Cyan, onClick = onRefresh)
                Spacer(Modifier.width(7.dp))
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Pink.copy(alpha=.25f), Lavender.copy(alpha=.15f))))
                        .border(1.dp, Pink.copy(alpha=.33f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painterResource(R.drawable.umay_logo), "Umay TV", Modifier.size(31.dp), contentScale = ContentScale.Fit)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Hairline))
    }
}

@Composable
private fun PremiumBottomDock(
    selected: AppSection,
    onSelected: (AppSection) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Midnight.copy(alpha=.995f))))
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(27.dp))
                .background(Brush.linearGradient(listOf(Color(0xF20A1222), Color(0xF50E1830))))
                .border(1.dp, StrongHairline, RoundedCornerShape(27.dp))
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSection.entries.forEach { item ->
                val active = selected == item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(
                            if (active) Brush.linearGradient(listOf(Pink.copy(alpha=.25f), Lavender.copy(alpha=.14f)))
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .border(if (active) 1.dp else 0.dp, if (active) Pink.copy(alpha=.22f) else Color.Transparent, RoundedCornerShape(19.dp))
                        .clickable { onSelected(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(sectionIcon(item), item.label, tint = if (active) PinkSoft else TextSecondary,
                            modifier = Modifier.size(if (active) 22.dp else 20.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(item.navLabel(), color = if (active) TextPrimary else TextSecondary,
                            fontSize = 9.sp, lineHeight = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    reminderOpenRequest: ReminderOpenRequest? = null,
    onReminderConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val useSamsungOneUi = remember { isSamsungDevice() }
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var guide by remember { mutableStateOf<GuideData?>(null) }
    var channelConfig by remember { mutableStateOf(DefaultChannelConfig) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var selectedDay by remember {
        mutableStateOf(LocalDate.now().plusDays(prefs.getInt("guide_day_offset", 0).toLong()))
    }
    var selectedGroup by remember {
        mutableStateOf(prefs.getString("guide_group", "All") ?: "All")
    }
    var selectedProgramme by remember { mutableStateOf<Programme?>(null) }
    var selectedChannel by remember { mutableStateOf<TvChannel?>(null) }
    var lastUpdated by remember { mutableStateOf<LocalTime?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favouriteShows by remember { mutableStateOf(loadFavouriteShows(context)) }
    var favouriteChannels by remember { mutableStateOf(loadFavouriteChannels(context)) }
    var reminderMode by remember { mutableStateOf(loadReminderMode(context)) }
    var showReminderModes by remember { mutableStateOf(loadShowReminderModes(context)) }
    var use24Hour by remember { mutableStateOf(prefs.getBoolean(PREF_TIME_24, true)) }
    var autoRefresh by remember { mutableStateOf(prefs.getBoolean(PREF_AUTO_REFRESH, true)) }
    var section by remember {
        mutableStateOf(
            runCatching {
                AppSection.valueOf(prefs.getString(PREF_DEFAULT_SECTION, AppSection.HOME.name)!!)
            }.getOrDefault(AppSection.HOME)
        )
    }

    LaunchedEffect(Unit) {
        if (prefs.getBoolean("open_schedule_once", false)) {
            section = AppSection.SCHEDULE
            prefs.edit().remove("open_schedule_once").apply()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(refreshToken) {
        loading = guide == null
        error = null
        try {
            guide = withContext(Dispatchers.IO) {
                XmlTvRepository.loadCached(context, GUIDE_URL, "guide-cache.xml")
            }
            channelConfig = withContext(Dispatchers.IO) {
                ChannelConfigRepository.loadCached(context, CHANNEL_CONFIG_URL, "channels-cache.json")
            }
            lastUpdated = LocalTime.now()
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    LaunchedEffect(autoRefresh) {
        BackgroundRefreshManager.configure(context, autoRefresh)
    }

    LaunchedEffect(Unit) {
        HouseholdScheduleSyncManager.configure(context)
        ScheduleDayScheduler.schedule(context)
    }

    LaunchedEffect(selectedDay) {
        val offset = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), selectedDay).toInt()
        prefs.edit().putInt("guide_day_offset", offset).apply()
    }

    LaunchedEffect(selectedGroup) {
        prefs.edit().putString("guide_group", selectedGroup).apply()
    }

    LaunchedEffect(guide, favouriteShows, reminderMode, showReminderModes) {
        val currentGuide = guide ?: return@LaunchedEffect
        ProgrammeReminderScheduler.scheduleAll(
            context = context,
            favouriteTitles = favouriteShows,
            programmes = currentGuide.programmes,
            channels = currentGuide.channels,
        )
    }

    LaunchedEffect(guide, reminderOpenRequest) {
        val currentGuide = guide ?: return@LaunchedEffect
        val request = reminderOpenRequest ?: return@LaunchedEffect
        val match = currentGuide.programmes
            .asSequence()
            .filter { sameShowTitle(it.title, request.title) }
            .filter { request.channelId.isBlank() || it.channelId == request.channelId }
            .minByOrNull { p ->
                if (request.startEpochMillis > 0L) {
                    kotlin.math.abs(p.start.toInstant().toEpochMilli() - request.startEpochMillis)
                } else 0L
            }
        if (match != null) {
            selectedDay = match.start.toLocalDate()
            selectedProgramme = match
            section = AppSection.GUIDE
        }
        onReminderConsumed()
    }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            PremiumTopBar(
                searchOpen = searchOpen,
                searchQuery = searchQuery,
                onSearchQuery = { searchQuery = it },
                onCloseSearch = { searchQuery = ""; searchOpen = false },
                lastUpdated = lastUpdated,
                use24Hour = use24Hour,
                isLandscape = isLandscape,
                onSearch = { searchOpen = true },
                onRefresh = { refreshToken++ },
            )
        },
        bottomBar = {
            if (!isLandscape) {
                PremiumBottomDock(section) { item ->
                    if (useSamsungOneUi) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    section = item
                    searchOpen = false
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (isLandscape) {
                NavigationRail(
                    containerColor = if (useSamsungOneUi) SurfaceHigh else Panel,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(76.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    AppSection.entries.forEach { item ->
                        val icon = when (item) {
                            AppSection.HOME -> Icons.Rounded.Home
                            AppSection.GUIDE -> Icons.Rounded.Tv
                            AppSection.FAVOURITES -> Icons.Rounded.Favorite
                            AppSection.SCHEDULE -> Icons.Rounded.CalendarMonth
                            AppSection.AI -> Icons.Rounded.Star
                            AppSection.SETTINGS -> Icons.Rounded.Settings
                        }
                        NavigationRailItem(
                            selected = section == item,
                            onClick = {
                                if (useSamsungOneUi) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                section = item
                                searchOpen = false
                            },
                            icon = {
                                Icon(
                                    icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(if (useSamsungOneUi) 27.dp else 24.dp)
                                )
                            },
                            label = {
                                if (!useSamsungOneUi) {
                                    Text(item.label, fontSize = 10.sp)
                                }
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Pink,
                                selectedTextColor = TextPrimary,
                                indicatorColor = Pink.copy(alpha = if (useSamsungOneUi) 0.22f else 0.16f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            )
                        )
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Midnight, Canvas, Midnight)))
                    .animateContentSize()
            ) {
                when {
                loading && guide == null -> LoadingView()
                error != null && guide == null -> ErrorView(error!!) { refreshToken++ }
                guide != null -> {
                    val currentGuide = guide!!
                    val visibleChannels = mergedChannels(currentGuide.channels, channelConfig)

                    if (searchOpen) {
                        SearchResultsView(
                            query = searchQuery,
                            guide = currentGuide,
                            channels = visibleChannels,
                            favouriteShows = favouriteShows,
                            onProgramme = { selectedProgramme = it },
                            use24Hour = use24Hour,
                        )
                    } else {
                        Crossfade(targetState = section, label = "mainSection") { activeSection ->
                            when (activeSection) {
                        AppSection.HOME -> HomeView(
                            guide = currentGuide,
                            channels = visibleChannels,
                            favouriteShows = favouriteShows,
                            onProgramme = { selectedProgramme = it },
                            onOpenGuide = { group -> selectedGroup = group; section = AppSection.GUIDE },
                            use24Hour = use24Hour,
                        )
                        AppSection.GUIDE -> DynamicGuideContent(
                            guide = currentGuide,
                            channels = visibleChannels,
                            channelConfig = channelConfig,
                            selectedDay = selectedDay,
                            onSelectedDay = { selectedDay = it },
                            selectedGroup = selectedGroup,
                            onGroup = { selectedGroup = it },
                            favouriteShows = favouriteShows,
                            onProgramme = { selectedProgramme = it },
                        )
                        AppSection.FAVOURITES -> FavouritesView(
                            guide = currentGuide,
                            channels = visibleChannels,
                            favouriteShows = favouriteShows,
                            favouriteChannels = favouriteChannels,
                            onProgramme = { selectedProgramme = it },
                            onChannel = { selectedChannel = it },
                            onExplore = { selectedGroup = "All"; section = AppSection.GUIDE },
                            use24Hour = use24Hour,
                        )
                        AppSection.SCHEDULE -> SharedScheduleView(
                            guide = currentGuide,
                            channels = visibleChannels,
                        )
                        AppSection.AI -> ChannelAdviserView(
                            guide = currentGuide,
                            channels = visibleChannels,
                        )
                        AppSection.SETTINGS -> SettingsView(
                            reminderMode = reminderMode,
                            onReminderMode = {
                                reminderMode = it
                                saveReminderMode(context, it)
                            },
                            use24Hour = use24Hour,
                            onUse24Hour = {
                                use24Hour = it
                                prefs.edit().putBoolean(PREF_TIME_24, it).apply()
                            },
                            autoRefresh = autoRefresh,
                            onAutoRefresh = {
                                autoRefresh = it
                                prefs.edit().putBoolean(PREF_AUTO_REFRESH, it).apply()
                            },
                            defaultSection = runCatching {
                                AppSection.valueOf(prefs.getString(PREF_DEFAULT_SECTION, AppSection.HOME.name)!!)
                            }.getOrDefault(AppSection.HOME),
                            onDefaultSection = {
                                prefs.edit().putString(PREF_DEFAULT_SECTION, it.name).apply()
                            },
                            channelConfig = channelConfig,
                            guide = currentGuide,
                            lastUpdated = lastUpdated,
                            use24HourForLabel = use24Hour,
                        )
                            }
                        }
                    }

                    if (loading) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Panel2.copy(alpha = 0.96f),
                            tonalElevation = 2.dp
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = Pink,
                                trackColor = Panel2
                            )
                        }
                    }
                }
            }
            }
        }
    }

    selectedProgramme?.let { programme ->
        val currentGuide = guide
        ModalBottomSheet(
            onDismissRequest = { selectedProgramme = null },
            containerColor = SurfaceSoft,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = {
                Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 42.dp, height = 4.dp).clip(RoundedCornerShape(99.dp)).background(TextTertiary.copy(alpha = .55f)))
            },
        ) {
            val isFavourite = favouriteShows.any { sameShowTitle(it, programme.title) }
            val effectiveReminderMode = showReminderModes[normaliseShowTitle(programme.title)] ?: reminderMode
            ProgrammeSheetV2(
                programme = programme,
                channel = currentGuide?.channels?.firstOrNull { it.id == programme.channelId },
                allProgrammes = currentGuide?.programmes.orEmpty(),
                isFavourite = isFavourite,
                isKidsChannel = channelConfig.firstOrNull { channelKey(it.id) == channelKey(programme.channelId) }?.group.equals("Kids", ignoreCase = true),
                reminderMode = effectiveReminderMode,
                use24Hour = use24Hour,
                onToggleFavourite = {
                    val title = programme.title.trim()
                    val updated = if (isFavourite) {
                        favouriteShows.filterNot { sameShowTitle(it, title) }.toSet()
                    } else favouriteShows + title
                    favouriteShows = updated
                    saveFavouriteShows(context, updated)
                    if (!isFavourite) {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        ProgrammeReminderScheduler.scheduleForTitle(
                            context = context,
                            title = title,
                            programmes = currentGuide?.programmes.orEmpty(),
                            channels = currentGuide?.channels.orEmpty(),
                            explicitMode = showReminderModes[normaliseShowTitle(title)] ?: reminderMode,
                        )
                    } else {
                        ProgrammeReminderScheduler.cancelForTitle(
                            context, title, currentGuide?.programmes.orEmpty()
                        )
                    }
                },
                onReminderMode = { mode ->
                    val key = normaliseShowTitle(programme.title)
                    val updatedModes = showReminderModes.toMutableMap()
                    updatedModes[key] = mode
                    showReminderModes = updatedModes
                    saveShowReminderModes(context, updatedModes)

                    if (mode != ReminderMode.OFF && !isFavourite) {
                        val updatedFavourites = favouriteShows + programme.title.trim()
                        favouriteShows = updatedFavourites
                        saveFavouriteShows(context, updatedFavourites)
                    }

                    if (Build.VERSION.SDK_INT >= 33 && mode != ReminderMode.OFF &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    ProgrammeReminderScheduler.cancelForTitle(
                        context, programme.title, currentGuide?.programmes.orEmpty()
                    )
                    if (mode != ReminderMode.OFF) {
                        ProgrammeReminderScheduler.scheduleForTitle(
                            context = context,
                            title = programme.title,
                            programmes = currentGuide?.programmes.orEmpty(),
                            channels = currentGuide?.channels.orEmpty(),
                            explicitMode = mode,
                        )
                    }
                },
                onProgramme = { selectedProgramme = it },
                onClose = { selectedProgramme = null }
            )
        }
    }

    selectedChannel?.let { channel ->
        ModalBottomSheet(
            onDismissRequest = { selectedChannel = null },
            containerColor = SurfaceSoft,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = {
                Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 42.dp, height = 4.dp).clip(RoundedCornerShape(99.dp)).background(TextTertiary.copy(alpha = .55f)))
            },
        ) {
            ChannelScheduleSheet(
                channel = channel,
                programmes = guide?.programmes.orEmpty(),
                isFavourite = favouriteChannels.contains(channel.id),
                onToggleFavourite = {
                    favouriteChannels = if (favouriteChannels.contains(channel.id)) {
                        favouriteChannels - channel.id
                    } else favouriteChannels + channel.id
                    saveFavouriteChannels(context, favouriteChannels)
                },
                onProgramme = { selectedProgramme = it },
                use24Hour = use24Hour,
                onClose = { selectedChannel = null }
            )
        }
    }
}


@Composable
private fun AppHeroCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color = Pink,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceSoft)))
            .border(1.dp, Color.White.copy(alpha=.07f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha=.26f), Lavender.copy(alpha=.10f))))
                .border(1.dp, accent.copy(alpha=.16f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = (-.35).sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
private fun PolishedSection(
    title: String,
    subtitle: String? = null,
    accent: Color = Pink,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceSoft)))
            .border(1.dp, Color.White.copy(alpha=.065f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha=.14f)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(7.dp).background(accent, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.2).sp)
                subtitle?.let { Text(it, color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp) }
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun CompactPill(text: String, accent: Color = Pink) {
    Surface(
        color = accent.copy(alpha = .12f),
        shape = RoundedCornerShape(100.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .16f))
    ) {
        Text(text, color = accent, fontWeight = FontWeight.Black, fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun PremiumSectionHeader(
    eyebrow: String? = null,
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(99.dp)).background(Brush.verticalGradient(listOf(Pink, Lavender))))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            eyebrow?.let { Text(it.uppercase(Locale.ROOT), color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp) }
            Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = (-.45).sp)
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(action, color = PinkSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(2.dp)); Icon(Icons.Rounded.KeyboardArrowRight, null, tint = PinkSoft, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun PremiumChoiceChip(
    text: String,
    selected: Boolean,
    accent: Color = Pink,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) accent.copy(alpha=.16f) else Color.White.copy(alpha=.035f))
            .border(1.dp, if (selected) accent.copy(alpha=.30f) else Hairline, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal=12.dp, vertical=8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { Icon(it, null, tint = if (selected) accent else TextSecondary, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (selected) TextPrimary else TextSecondary, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun HomeView(
    guide: GuideData,
    channels: List<TvChannel>,
    favouriteShows: Set<String>,
    onProgramme: (Programme) -> Unit,
    onOpenGuide: (String) -> Unit,
    use24Hour: Boolean,
) {
    val now = ZonedDateTime.now()
    val onNow = guide.programmes
        .filter { !now.isBefore(it.start) && now.isBefore(it.stop) }
        .distinctBy { Triple(channelKey(it.channelId), normaliseShowTitle(it.title), it.start.toInstant()) }
        .sortedBy { p -> channels.indexOfFirst { channelKey(it.id) == channelKey(p.channelId) }.let { if (it < 0) Int.MAX_VALUE else it } }
    val startingSoon = guide.programmes
        .filter { it.start.isAfter(now) && !it.start.isAfter(now.plusMinutes(30)) }
        .distinctBy { Triple(channelKey(it.channelId), normaliseShowTitle(it.title), it.start.toInstant()) }
        .sortedBy { it.start }.take(8)
    val favouriteUpcoming = guide.programmes
        .filter { it.start.isAfter(now) && favouriteShows.any { fav -> sameShowTitle(fav, it.title) } }
        .sortedBy { it.start }.take(8)
    val heroProgramme = onNow.firstOrNull()
    val heroChannel = heroProgramme?.let { p -> channels.firstOrNull { channelKey(it.id) == channelKey(p.channelId) || channelKey(it.name) == channelKey(p.channelId) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF13213A), Color(0xFF0B1427), Color(0xFF08101D))))
                        .border(1.dp, StrongHairline, RoundedCornerShape(30.dp))
                        .clickable(enabled = heroProgramme != null) { heroProgramme?.let(onProgramme) }
                ) {
                    if (heroProgramme?.icon?.isNotBlank() == true) {
                        AsyncImage(
                            model = heroProgramme.icon,
                            contentDescription = heroProgramme.title,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.64f),
                            contentScale = ContentScale.Crop,
                        )
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF20A1220), Color(0xB80A1220), Color(0x300A1220)))))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB8050912)))))
                    } else {
                        Box(Modifier.align(Alignment.TopEnd).size(220.dp).background(Brush.radialGradient(listOf(Pink.copy(alpha=.20f), Color.Transparent))))
                        Box(Modifier.align(Alignment.BottomStart).size(170.dp).background(Brush.radialGradient(listOf(Cyan.copy(alpha=.10f), Color.Transparent))))
                    }

                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Mint.copy(alpha=.14f), shape = RoundedCornerShape(100.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Mint.copy(alpha=.22f))) {
                                Row(Modifier.padding(horizontal=10.dp, vertical=6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).background(Mint, CircleShape)); Spacer(Modifier.width(6.dp))
                                    Text("LIVE NOW", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            CompactPill("TODAY", Lavender)
                        }

                        Spacer(Modifier.weight(1f))
                        if (heroProgramme != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Panel2.copy(alpha=.88f)).border(1.dp, StrongHairline, RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val channelLogo = heroChannel?.icon
                                    if (!channelLogo.isNullOrBlank()) AsyncImage(channelLogo, heroChannel.name, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                                    else LogoFallback(heroChannel?.name ?: heroProgramme.channelId)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(heroProgramme.title, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-.65).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text(heroChannel?.name ?: heroProgramme.channelId, color = PinkSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            val total = Duration.between(heroProgramme.start, heroProgramme.stop).toMinutes().coerceAtLeast(1)
                            val elapsed = Duration.between(heroProgramme.start, now).toMinutes().coerceIn(0, total)
                            LinearProgressIndicator(
                                progress = { elapsed.toFloat() / total.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),
                                color = Pink, trackColor = Color.White.copy(alpha=.10f),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatTime(heroProgramme.start.toLocalTime(), use24Hour), color = TextSecondary, fontSize = 10.sp)
                                Spacer(Modifier.width(8.dp)); Box(Modifier.size(3.dp).background(TextTertiary, CircleShape)); Spacer(Modifier.width(8.dp))
                                Text("${(total - elapsed).coerceAtLeast(0)} min left", color = TextSecondary, fontSize = 10.sp)
                                Spacer(Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Open details", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = PinkSoft, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            Text("Nothing is live right now", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("The next programme will appear here automatically when the guide catches up.", color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf(
                        Triple("ON NOW", onNow.size.toString(), Cyan),
                        Triple("NEXT 30M", startingSoon.size.toString(), Pink),
                        Triple("FAVES", favouriteUpcoming.size.toString(), Lavender),
                    ).forEach { (label, value, accent) ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(listOf(accent.copy(alpha=.08f), SurfaceHigh.copy(alpha=.82f))))
                                .border(1.dp, accent.copy(alpha=.12f), RoundedCornerShape(18.dp)).padding(horizontal=12.dp, vertical=11.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(value, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 21.sp)
                                Text(label, color = accent, fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = .65.sp)
                            }
                        }
                    }
                }
            }
        }

        if (onNow.isNotEmpty()) {
            item {
                PremiumSectionHeader(eyebrow = "LIVE", title = "On now", action = "See guide") { onOpenGuide("All") }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(end = 2.dp)) {
                    items(onNow) { p ->
                        ProgrammePosterCard(p, channels.firstOrNull { channelKey(it.id) == channelKey(p.channelId) }, use24Hour) { onProgramme(p) }
                    }
                }
            }
        }

        if (startingSoon.isNotEmpty()) {
            item {
                PremiumSectionHeader(eyebrow = "NEXT", title = "Starting soon", action = "See all") { onOpenGuide("All") }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceSoft.copy(alpha=.92f))
                        .border(1.dp, Hairline, RoundedCornerShape(24.dp)).padding(vertical = 3.dp)
                ) {
                    startingSoon.take(3).forEachIndexed { index, p ->
                        val channel = channels.firstOrNull { channelKey(it.id) == channelKey(p.channelId) }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onProgramme(p) }.padding(horizontal=11.dp, vertical=10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = Pink.copy(alpha=.12f), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Pink.copy(alpha=.12f))) {
                                Text(formatTime(p.start.toLocalTime(), use24Hour), color = PinkSoft, fontWeight = FontWeight.Black, fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal=8.dp, vertical=7.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Panel2).border(1.dp, Hairline, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                                if (!channel?.icon.isNullOrBlank()) AsyncImage(channel?.icon, channel?.name, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                                else LogoFallback(channel?.name ?: p.channelId)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                Text(channel?.name ?: p.channelId, color = TextSecondary, fontSize = 9.sp)
                            }
                            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = TextTertiary, modifier = Modifier.size(19.dp))
                        }
                        if (index < minOf(2, startingSoon.lastIndex)) HorizontalDivider(color = Hairline, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }

    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    PremiumSectionHeader(title = title, action = "See all", onAction = onSeeAll)
}

@Composable
private fun ChannelTile(channel: TvChannel, onClick: () -> Unit) {
    Box(
        modifier = Modifier.width(118.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(SurfaceHigh, SurfaceSoft)))
            .border(1.dp, Hairline, RoundedCornerShape(22.dp)).clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Panel2.copy(alpha=.88f)).border(1.dp, Hairline, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.icon.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = channel.icon,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(7.dp)
                    ) {
                        if (painter.state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent() else LogoFallback(channel.name)
                    }
                } else LogoFallback(channel.name)
            }
            Spacer(Modifier.height(8.dp))
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NowNextRow(
    channel: TvChannel,
    current: Programme?,
    next: Programme?,
    use24Hour: Boolean,
    onChannel: (TvChannel) -> Unit,
    onProgramme: (Programme) -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Panel),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.icon.isNullOrBlank()) {
                    AsyncImage(channel.icon, channel.name, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit)
                } else LogoFallback(channel.name)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = PinkSoft, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onChannel(channel) })
                current?.let { p ->
                    Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onProgramme(p) })
                    val total = Duration.between(p.start, p.stop).toMinutes().coerceAtLeast(1)
                    val elapsed = Duration.between(p.start, ZonedDateTime.now()).toMinutes().coerceIn(0, total)
                    LinearProgressIndicator(
                        progress = { elapsed.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)),
                        color = Pink, trackColor = Color(0xFF2A3042)
                    )
                    Text("${(total - elapsed).coerceAtLeast(0)} min left", color = TextSecondary, fontSize = 10.sp)
                } ?: Text("Nothing listed right now", color = TextSecondary, fontSize = 12.sp)
                next?.let { p ->
                    Text("Next ${formatTime(p.start.toLocalTime(), use24Hour)} • ${p.title}", color = TextSecondary, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ProgrammePosterCard(
    programme: Programme,
    channel: TvChannel?,
    use24Hour: Boolean,
    onClick: () -> Unit,
) {
    val now = ZonedDateTime.now()
    val isLive = !now.isBefore(programme.start) && now.isBefore(programme.stop)
    Box(
        modifier = Modifier.width(210.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(SurfaceHigh, SurfaceSoft)))
            .border(1.dp, if (isLive) Pink.copy(alpha=.28f) else Hairline, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(118.dp).background(Panel)) {
                val hasArtwork = !programme.icon.isNullOrBlank()
                val image = programme.icon ?: channel?.icon
                if (!image.isNullOrBlank()) {
                    AsyncImage(
                        image,
                        programme.title,
                        Modifier.fillMaxSize().padding(if (hasArtwork) 0.dp else 16.dp),
                        contentScale = if (hasArtwork) ContentScale.Crop else ContentScale.Fit,
                    )
                    if (hasArtwork) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB8080D18)))))
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Panel3, Panel))))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LogoFallback(channel?.name ?: programme.channelId) }
                }
                if (isLive) {
                    Surface(modifier = Modifier.align(Alignment.TopStart).padding(9.dp), color = Pink, shape = RoundedCornerShape(100.dp)) {
                        Row(Modifier.padding(horizontal=8.dp, vertical=4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).background(Color(0xFF25000F), CircleShape)); Spacer(Modifier.width(5.dp))
                            Text("LIVE", color = Color(0xFF25000F), fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing=.7.sp)
                        }
                    }
                    val total = Duration.between(programme.start, programme.stop).toMinutes().coerceAtLeast(1)
                    val elapsed = Duration.between(programme.start, now).toMinutes().coerceIn(0, total)
                    Surface(modifier = Modifier.align(Alignment.TopEnd).padding(9.dp), color = Color(0xC90A1220), shape = RoundedCornerShape(100.dp)) {
                        Text("${(total-elapsed).coerceAtLeast(0)} min left", color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp))
                    }
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(programme.title, fontWeight = FontWeight.Black, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(channel?.name ?: programme.channelId, color = PinkSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines=1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(programme.start.toLocalTime(), use24Hour), color = TextSecondary, fontSize = 9.sp)
                    Text(" – ", color = TextTertiary, fontSize = 9.sp)
                    Text(formatTime(programme.stop.toLocalTime(), use24Hour), color = TextSecondary, fontSize = 9.sp)
                }
                if (isLive) {
                    val total = Duration.between(programme.start, programme.stop).toMinutes().coerceAtLeast(1)
                    val elapsed = Duration.between(programme.start, now).toMinutes().coerceIn(0, total)
                    LinearProgressIndicator(progress = { elapsed.toFloat()/total.toFloat() }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(100.dp)), color = Pink, trackColor = Color.White.copy(alpha=.08f))
                }
            }
        }
    }
}

@Composable
private fun DynamicGuideContent(
    guide: GuideData,
    channels: List<TvChannel>,
    channelConfig: List<ChannelConfig>,
    selectedDay: LocalDate,
    onSelectedDay: (LocalDate) -> Unit,
    selectedGroup: String,
    onGroup: (String) -> Unit,
    favouriteShows: Set<String>,
    onProgramme: (Programme) -> Unit,
) {
    var jumpTarget by rememberSaveable { mutableStateOf(GuideJumpTarget.NOW) }
    val configById = channelConfig.associateBy { channelKey(it.id) }
    val groups = listOf("All", "Favourite Shows") + channelConfig
        .map { it.group }.filter { it.isNotBlank() }.distinct()
    val visible = channels.filter { channel ->
        when (selectedGroup) {
            "All" -> true
            "Favourite Shows" -> guide.programmes.any { p ->
                p.channelId == channel.id && p.start.toLocalDate() == selectedDay &&
                    favouriteShows.any { sameShowTitle(it, p.title) }
            }
            else -> configById[channelKey(channel.id)]?.group == selectedGroup
        }
    }

    val guideConfig = LocalConfiguration.current
    val guideLandscape = guideConfig.screenWidthDp > guideConfig.screenHeightDp
    var landscapeFiltersExpanded by rememberSaveable { mutableStateOf(false) }
    var portraitFiltersExpanded by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (guideLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { landscapeFiltersExpanded = !landscapeFiltersExpanded },
                    shape = RoundedCornerShape(if (isSamsungDevice()) 28.dp else 24.dp),
                    label = { Text(if (landscapeFiltersExpanded) "Hide filters" else "Filters") }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${if (selectedDay == LocalDate.now()) "Today" else selectedDay.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))} • $selectedGroup",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {
                        onSelectedDay(LocalDate.now())
                        jumpTarget = GuideJumpTarget.NOW
                    },
                    shape = RoundedCornerShape(if (isSamsungDevice()) 28.dp else 24.dp),
                    label = { Text("NOW") }
                )
            }

            if (landscapeFiltersExpanded) {
                DayPicker(selectedDay, onSelectedDay)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        shape = RoundedCornerShape(if (isSamsungDevice()) 28.dp else 26.dp),
                        onClick = { onSelectedDay(LocalDate.now()); jumpTarget = GuideJumpTarget.NOW },
                        label = { Text("NOW") }
                    )
                    AssistChip(
                        shape = RoundedCornerShape(if (isSamsungDevice()) 28.dp else 26.dp),
                        onClick = { onSelectedDay(LocalDate.now()); jumpTarget = GuideJumpTarget.TONIGHT },
                        label = { Text("Tonight") }
                    )
                    AssistChip(
                        shape = RoundedCornerShape(if (isSamsungDevice()) 28.dp else 26.dp),
                        onClick = { onSelectedDay(LocalDate.now().plusDays(1)); jumpTarget = GuideJumpTarget.TOMORROW },
                        label = { Text("Tomorrow") }
                    )
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups.distinct()) { group ->
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { onGroup(group) },
                            label = { Text(group) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Pink,
                                selectedLabelColor = Color(0xFF210012),
                                containerColor = Panel2,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=7.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceSoft.copy(alpha=.72f))
                    .border(1.dp, Hairline, RoundedCornerShape(22.dp))
                    .padding(horizontal=12.dp, vertical=11.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LIVE GUIDE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                        Text(
                            if (selectedDay == LocalDate.now()) "What’s on today" else selectedDay.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK)),
                            color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = (-.4).sp
                        )
                    }
                    Surface(color = Mint.copy(alpha=.11f), shape=RoundedCornerShape(100.dp), border=androidx.compose.foundation.BorderStroke(1.dp, Mint.copy(alpha=.17f))) {
                        Text("${visible.size} channels", color=Mint, fontWeight=FontWeight.Bold, fontSize=9.sp, modifier=Modifier.padding(horizontal=9.dp, vertical=6.dp))
                    }
                }
                DayPicker(selectedDay, onSelectedDay)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    PremiumChoiceChip("Now", jumpTarget == GuideJumpTarget.NOW, Pink, Icons.Rounded.PlayArrow) {
                        onSelectedDay(LocalDate.now()); jumpTarget = GuideJumpTarget.NOW
                    }
                    PremiumChoiceChip("Tonight", jumpTarget == GuideJumpTarget.TONIGHT, Lavender, Icons.Rounded.Schedule) {
                        onSelectedDay(LocalDate.now()); jumpTarget = GuideJumpTarget.TONIGHT
                    }
                    PremiumChoiceChip(
                        if (selectedGroup == "All") "Channels" else selectedGroup,
                        portraitFiltersExpanded || selectedGroup != "All",
                        Cyan,
                        Icons.Rounded.Tv
                    ) { portraitFiltersExpanded = !portraitFiltersExpanded }
                }
                AnimatedVisibility(visible = portraitFiltersExpanded) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(groups.distinct()) { group ->
                            PremiumChoiceChip(group, selectedGroup == group, if (group == "Kids") Pink else if (group == "Turkish TV") Lavender else Cyan) {
                                onGroup(group)
                                portraitFiltersExpanded = false
                            }
                        }
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels in this section", color = TextSecondary)
            }
        } else {
            TvGrid(visible, guide.programmes, selectedDay, favouriteShows, jumpTarget, onProgramme)
        }
    }
}

@Composable
private fun SearchResultsView(
    query: String,
    guide: GuideData,
    channels: List<TvChannel>,
    favouriteShows: Set<String>,
    onProgramme: (Programme) -> Unit,
    use24Hour: Boolean,
) {
    val normalised = searchNormalise(query)
    val results = if (normalised.length < 2) emptyList() else guide.programmes
        .filter { p ->
            searchNormalise(p.title).contains(normalised) ||
                searchNormalise(p.description.orEmpty()).contains(normalised)
        }
        .filter { it.stop.isAfter(ZonedDateTime.now().minusHours(1)) }
        .sortedBy { it.start }
        .take(100)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PremiumSectionHeader(
                eyebrow = "SEARCH",
                title = if (query.length < 2) "Start typing" else "${results.size} results"
            )
            Text(
                if (query.length < 2) "Enter at least two characters." else "Matches from the live guide and descriptions.",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        items(results) { p ->
            ProgrammeListRow(
                p,
                channels.firstOrNull { it.id == p.channelId },
                favouriteShows.any { sameShowTitle(it, p.title) },
                use24Hour,
                onProgramme
            )
        }
    }
}

@Composable
private fun FavouritesView(
    guide: GuideData,
    channels: List<TvChannel>,
    favouriteShows: Set<String>,
    favouriteChannels: Set<String>,
    onProgramme: (Programme) -> Unit,
    onChannel: (TvChannel) -> Unit,
    onExplore: () -> Unit,
    use24Hour: Boolean,
) {
    val now = ZonedDateTime.now()
    val upcoming = guide.programmes
        .filter { it.stop.isAfter(now) && favouriteShows.any { f -> sameShowTitle(f, it.title) } }
        .sortedBy { it.start }
    val todayCount = upcoming.count { it.start.toLocalDate() == now.toLocalDate() }
    var showAllUpcoming by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Pink.copy(alpha=.16f), SurfaceHigh, SurfaceSoft)))
                    .border(1.dp, Pink.copy(alpha=.14f), RoundedCornerShape(30.dp)).padding(18.dp)
            ) {
                Box(Modifier.align(Alignment.TopEnd).size(150.dp).background(Brush.radialGradient(listOf(Pink.copy(alpha=.16f), Color.Transparent))))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Pink.copy(alpha=.13f)), contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.Favorite, null, tint = PinkSoft, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("YOUR LIBRARY", color = PinkSoft, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
                            Text("Household favourites", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing=(-.45).sp)
                        }
                    }
                    Text("Saved shows and pinned channels stay together here, with the next useful airing surfaced first.", color = TextSecondary, fontSize = 10.sp, lineHeight = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("TODAY", todayCount.toString(), Pink),
                            Triple("SHOWS", favouriteShows.size.toString(), Cyan),
                            Triple("CHANNELS", favouriteChannels.size.toString(), Lavender),
                        ).forEach { (label, value, accent) ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.035f)).border(1.dp, Hairline, RoundedCornerShape(16.dp)).padding(10.dp)) {
                                Column { Text(value, fontWeight=FontWeight.Black, fontSize=18.sp); Text(label, color=accent, fontSize=8.sp, fontWeight=FontWeight.Black, letterSpacing=.6.sp) }
                            }
                        }
                    }
                }
            }
        }

        if (favouriteChannels.isNotEmpty()) {
            item {
                PremiumSectionHeader(eyebrow="PINNED", title="Favourite channels")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(channels.filter { favouriteChannels.contains(it.id) }) { ch -> ChannelTile(ch) { onChannel(ch) } }
                }
            }
        }

        if (upcoming.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceSoft)))
                        .border(1.dp, Hairline, RoundedCornerShape(28.dp)).padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Pink.copy(alpha=.12f)), contentAlignment=Alignment.Center) {
                                Icon(Icons.Rounded.FavoriteBorder, null, tint=PinkSoft, modifier=Modifier.size(26.dp))
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text("No saved shows yet", fontWeight=FontWeight.Black, fontSize=18.sp)
                                Text("Heart a programme once and future airings will appear here automatically.", color=TextSecondary, fontSize=10.sp, lineHeight=15.sp)
                            }
                        }
                        Button(
                            onClick = onExplore,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Color(0xFF25000F)),
                        ) {
                            Icon(Icons.Rounded.Tv, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Browse the guide", fontWeight=FontWeight.Black)
                        }
                    }
                }
            }
        } else {
            item {
                PremiumSectionHeader(
                    eyebrow="UP NEXT",
                    title="Coming up",
                    action=if (upcoming.size > 10) { if (showAllUpcoming) "Show less" else "Show all" } else null
                ) { showAllUpcoming = !showAllUpcoming }
            }
            items(if (showAllUpcoming) upcoming else upcoming.take(10)) { p ->
                ProgrammeListRow(p, channels.firstOrNull { channelKey(it.id) == channelKey(p.channelId) }, true, use24Hour, onProgramme)
            }
        }
    }
}

@Composable
private fun ProgrammeListRow(
    programme: Programme,
    channel: TvChannel?,
    favourite: Boolean,
    use24Hour: Boolean,
    onProgramme: (Programme) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(SurfaceHigh.copy(alpha=.88f), SurfaceSoft)))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp)).clickable { onProgramme(programme) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hasArtwork = !programme.icon.isNullOrBlank()
        val image = programme.icon ?: channel?.icon
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Hairline, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            if (!image.isNullOrBlank()) AsyncImage(image, programme.title, Modifier.fillMaxSize().padding(if (hasArtwork) 0.dp else 7.dp), contentScale = if (hasArtwork) ContentScale.Crop else ContentScale.Fit)
            else LogoFallback(channel?.name ?: programme.channelId)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(programme.title, modifier=Modifier.weight(1f), fontWeight=FontWeight.Black, maxLines=2, overflow=TextOverflow.Ellipsis, fontSize=13.sp, lineHeight=16.sp)
                if (favourite) { Spacer(Modifier.width(5.dp)); Icon(Icons.Rounded.Favorite, null, tint=PinkSoft, modifier=Modifier.size(15.dp)) }
            }
            Spacer(Modifier.height(2.dp))
            Text(channel?.name ?: programme.channelId, color=PinkSoft, fontSize=9.sp, fontWeight=FontWeight.Bold)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(programme.start.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)), color=TextSecondary, fontSize=9.sp)
                Spacer(Modifier.width(6.dp)); Box(Modifier.size(3.dp).background(TextTertiary, CircleShape)); Spacer(Modifier.width(6.dp))
                Text(formatTime(programme.start.toLocalTime(), use24Hour), color=TextSecondary, fontSize=9.sp)
            }
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint=TextTertiary, modifier=Modifier.size(19.dp))
    }
}

private data class AdviserRecommendation(
    val heading: String,
    val body: String,
    val kind: String = "info",
)

private data class AdviserProgrammeSuggestion(
    val programme: String,
    val channel: String,
    val reason: String,
)

private data class AdviserQuestionAnswer(
    val answer: String,
    val programmes: List<AdviserProgrammeSuggestion> = emptyList(),
)

private data class SuggestedChannel(
    val name: String,
    val language: String,
    val minMonths: Int,
    val maxMonths: Int,
    val reason: String,
    val bestFor: String,
)

private fun channelAdviserRecommendations(
    ageMonths: Int,
    guide: GuideData,
    channels: List<TvChannel>,
): List<AdviserRecommendation> {
    val kids = channels.filter { it.group.equals("Kids", ignoreCase = true) }
    val now = ZonedDateTime.now()
    val horizon = now.plusDays(7)
    val byChannel = guide.programmes
        .filter { !it.start.isBefore(now.minusHours(2)) && it.start.isBefore(horizon) }
        .groupBy { channelKey(it.channelId) }

    fun suitableScore(channel: TvChannel): Pair<Int, Int> {
        val shows = byChannel[channelKey(channel.id)].orEmpty().take(80)
        var suitable = 0
        var assessed = 0
        shows.forEach { programme ->
            val g = programmeAgeGuidance(programme, true).label
            if (!g.startsWith("ADULT")) {
                assessed++
                val ok = when (g) {
                    "0–12m" -> ageMonths <= 12
                    "6m–2y" -> ageMonths in 6..24
                    "1–3y" -> ageMonths in 12..36
                    "2–5y" -> ageMonths in 24..60
                    "2–4y" -> ageMonths in 24..48
                    "4y+" -> ageMonths >= 48
                    else -> false
                }
                if (ok) suitable++
            }
        }
        return suitable to assessed
    }

    val ranked = kids.map { it to suitableScore(it) }
        .sortedByDescending { (_, score) -> if (score.second == 0) -1.0 else score.first.toDouble() / score.second }
    val best = ranked.filter { it.second.second >= 2 }.take(3).map { it.first.name }
    val low = ranked.filter { it.second.second >= 3 && it.second.first * 3 < it.second.second }.take(3).map { it.first.name }

    val stage = when {
        ageMonths < 6 -> "sensory pictures, faces, soothing music and very simple repetition"
        ageMonths < 9 -> "simple songs, repetition, first sounds, colours and sensory programmes"
        ageMonths < 12 -> "songs, first words, simple cause-and-effect and short stories"
        ageMonths < 18 -> "first words, movement, repetition and simple toddler stories"
        ageMonths < 24 -> "language, counting, music and simple social-play stories"
        ageMonths < 36 -> "preschool language, imaginative play, counting and early problem-solving"
        else -> "preschool stories, learning, problem-solving and age-appropriate adventures"
    }

    val result = mutableListOf<AdviserRecommendation>()
    result += AdviserRecommendation(
        "Offline fallback",
        "At ${formatAge(ageMonths)}, prioritise $stage.",
        "focus"
    )
    if (best.isNotEmpty()) result += AdviserRecommendation(
        "Strongest matches in your guide",
        best.joinToString() + " have the best match from programmes with enough EPG information to assess.",
        "keep"
    )
    if (low.isNotEmpty()) result += AdviserRecommendation(
        "Better saved for later",
        low.joinToString() + " currently skew older. There is no need to remove them — they can simply sit lower in the guide for now.",
        "later"
    )

    val currentKeys = channels.flatMap { listOf(channelKey(it.id), channelKey(it.name)) }.toSet()
    val catalogue = listOf(
        SuggestedChannel("BabyTV", "English", 0, 36, "dedicated baby-first channel with short music, movement and first-concept programmes", "Baby-focused"),
        SuggestedChannel("Sensical Jr.", "English", 6, 36, "younger-viewer service with early-learning and preschool content", "Early learning"),
        SuggestedChannel("The Wiggles Channel", "English", 10, 48, "action songs, movement, repetition and clear spoken English", "Music & movement"),
        SuggestedChannel("Tiny Pop", "English", 18, 60, "gentle preschool stories and familiar UK children's programming", "Preschool stories"),
        SuggestedChannel("Cartoonito", "English", 24, 60, "preschool story-led programmes and social themes", "Preschool"),
        SuggestedChannel("Niloya", "Turkish", 12, 60, "simple Turkish everyday stories and songs", "Turkish language"),
        SuggestedChannel("Kukuli", "Turkish", 12, 60, "Turkish songs, repetition and short stories", "Turkish music"),
        SuggestedChannel("Minika GO", "Turkish", 48, 60, "a Turkish option for older children", "Older Turkish kids"),
    )
    fun alreadyPresent(s: SuggestedChannel): Boolean {
        val key = channelKey(s.name)
        return key in currentKeys || currentKeys.any { it.contains(key) || key.contains(it) }
    }
    val suggestions = catalogue
        .filter { ageMonths in it.minMonths..it.maxMonths }
        .filterNot(::alreadyPresent)
        .take(2)

    suggestions.forEach { suggestion ->
        result += AdviserRecommendation(
            "${suggestion.name}  •  ${suggestion.language}",
            "${suggestion.bestFor} — ${suggestion.reason}. Stream and EPG still need verifying.",
            "add"
        )
    }
    return result
}

private fun onNowKidsProgrammes(guide: GuideData, channels: List<TvChannel>): List<Programme> {
    val now = ZonedDateTime.now()
    val kids = channels.filter { it.group.equals("Kids", ignoreCase = true) }
    val kidsKeys = kids.flatMap { listOf(channelKey(it.id), channelKey(it.name)) }.toSet()
    val guideKidsIds = guide.channels.filter { guideChannel ->
        channelKey(guideChannel.id) in kidsKeys || channelKey(guideChannel.name) in kidsKeys ||
            guideChannel.group.equals("Kids", ignoreCase = true)
    }.flatMap { listOf(channelKey(it.id), channelKey(it.name)) }.toSet()
    val allKidsKeys = kidsKeys + guideKidsIds
    return guide.programmes.filter { p ->
        val pKey = channelKey(p.channelId)
        pKey in allKidsKeys && !p.start.isAfter(now) && p.stop.isAfter(now)
    }.sortedWith(compareBy<Programme>({ channelKey(it.channelId) }, { it.start }))
}

private suspend fun fetchCloudflareAdviserRecommendations(
    ageMonths: Int,
    guide: GuideData,
    channels: List<TvChannel>,
): List<AdviserRecommendation> = withContext(Dispatchers.IO) {
    val requestBody = JSONObject()
    requestBody.put("ageMonths", ageMonths)

    // Send only the live Kids lineup. This is rebuilt from the current channel
    // configuration every time Ask AI is tapped, so adding/removing a Kids
    // channel never requires changing the Worker prompt or rebuilding this list.
    val kidsChannels = channels.filter { it.group.equals("Kids", ignoreCase = true) }
    val currentChannels = JSONArray()
    kidsChannels.forEach { channel -> currentChannels.put(channel.name) }
    requestBody.put("currentChannels", currentChannels)

    requestBody.put("excludedChannels", JSONArray(listOf(
        "Super Simple Songs",
        "LooLoo Kids",
        "HappyKids",
        "HappyKids Junior",
        "Ketchup TV",
        "Kartoon Channel",
        "Baby Einstein",
    )))

    // Keep Ask AI fast: send only programmes that are ON NOW on Kids channels.
    // This gives the AI live programme-level context without sending the wider EPG.
    val now = ZonedDateTime.now()
    val kidsChannelKeys = kidsChannels.flatMap { channel ->
        listOf(channelKey(channel.id), channelKey(channel.name))
    }.toSet()
    val programmes = JSONArray()
    onNowKidsProgrammes(guide, channels).forEach { programme ->
            programmes.put(JSONObject().apply {
                put("channel", programme.channelId)
                put("title", programme.title)
                put("description", programme.description.orEmpty().replace("\n", " ").replace("\r", " ").trim().take(220))
                put("category", programme.category.orEmpty())
                put("language", if (channelKey(programme.channelId) in setOf(channelKey("TRT Çocuk"), channelKey("Minika Çocuk"))) "Turkish" else "English")
                put("start", programme.start.toLocalTime().toString().take(5))
                put("stop", programme.stop.toLocalTime().toString().take(5))
            })
        }
    requestBody.put("epgDate", now.toLocalDate().toString())
    requestBody.put("epgWindowStart", now.toLocalTime().toString().take(5))
    requestBody.put("epgWindowHours", 0)
    requestBody.put("epgAvailable", programmes.length() > 0)
    requestBody.put("programmes", programmes)

    val connection = (URL(AI_ADVISER_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 12_000
        readTimeout = 75_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
    }

    try {
        connection.outputStream.use { it.write(requestBody.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Cloudflare adviser returned HTTP $code")

        val root = JSONObject(text)
        if (root.has("error")) error(root.optString("error", "AI request failed"))

        val out = mutableListOf<AdviserRecommendation>()
        root.optString("summary").takeIf { it.isNotBlank() }?.let {
            out += AdviserRecommendation("AI recommendation", it, "focus")
        }
        root.optString("focus").takeIf { it.isNotBlank() }?.let {
            out += AdviserRecommendation("Best focus for this age", it, "focus")
        }

        val existing = root.optJSONArray("existing") ?: JSONArray()
        for (i in 0 until existing.length()) {
            val item = existing.optJSONObject(i) ?: continue
            val channel = item.optString("channel").trim()
            val verdict = item.optString("verdict").uppercase(Locale.ROOT)
            val reason = item.optString("reason").trim()
            if (channel.isBlank() || reason.isBlank()) continue
            val kind = when (verdict) {
                "KEEP" -> "keep"
                "LOWER", "LATER" -> "later"
                else -> "info"
            }
            val label = when (verdict) {
                "KEEP" -> "Keep prominent"
                "LOWER" -> "Move lower for now"
                "LATER" -> "Better for later"
                else -> "Review"
            }
            out += AdviserRecommendation("$channel  •  $label", reason, kind)
        }

        val currentKeys = channels.flatMap { listOf(channelKey(it.id), channelKey(it.name)) }.toSet()
        val add = root.optJSONArray("add") ?: JSONArray()
        for (i in 0 until add.length()) {
            val item = add.optJSONObject(i) ?: continue
            val channel = item.optString("channel").trim()
            val language = item.optString("language").trim()
            val priority = item.optString("priority").trim()
            val ageFit = item.optString("ageFit").trim()
            val reason = item.optString("reason").trim()
            if (channel.isBlank() || reason.isBlank()) continue
            val key = channelKey(channel)
            if (key in currentKeys || currentKeys.any { it == key }) continue
            if (!language.equals("English", true) && !language.equals("Turkish", true)) continue
            val meta = listOf(language, ageFit, priority.takeIf { it.isNotBlank() }?.let { "$it priority" })
                .filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")
            out += AdviserRecommendation(
                if (meta.isBlank()) channel else "$channel  •  $meta",
                "$reason Stream and EPG availability still need verifying before it is added.",
                "add"
            )
        }

        if (out.none { it.kind == "add" }) {
            out += AdviserRecommendation(
                "No strong new channel suggestion",
                "The AI did not find a worthwhile English or Turkish addition that is not already in your lineup.",
                "add"
            )
        }
        out
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchCloudflareAdviserAnswer(
    ageMonths: Int,
    question: String,
    guide: GuideData,
    channels: List<TvChannel>,
): AdviserQuestionAnswer = withContext(Dispatchers.IO) {
    val requestBody = JSONObject()
    requestBody.put("ageMonths", ageMonths)
    requestBody.put("question", question.trim().take(500))

    val kidsChannels = channels.filter { it.group.equals("Kids", ignoreCase = true) }
    val currentChannels = JSONArray()
    kidsChannels.forEach { channel -> currentChannels.put(channel.name) }
    requestBody.put("currentChannels", currentChannels)
    requestBody.put("excludedChannels", JSONArray(listOf(
        "Super Simple Songs",
        "LooLoo Kids",
        "HappyKids",
        "HappyKids Junior",
        "Ketchup TV",
        "Kartoon Channel",
        "Baby Einstein",
    )))

    // Keep Ask AI fast: send only programmes that are ON NOW on Kids channels.
    // This gives the AI live programme-level context without sending the wider EPG.
    val now = ZonedDateTime.now()
    val kidsChannelKeys = kidsChannels.flatMap { channel ->
        listOf(channelKey(channel.id), channelKey(channel.name))
    }.toSet()
    val programmes = JSONArray()
    onNowKidsProgrammes(guide, channels).forEach { programme ->
            programmes.put(JSONObject().apply {
                put("channel", programme.channelId)
                put("title", programme.title)
                put("description", programme.description.orEmpty().replace("\n", " ").replace("\r", " ").trim().take(220))
                put("category", programme.category.orEmpty())
                put("language", if (channelKey(programme.channelId) in setOf(channelKey("TRT Çocuk"), channelKey("Minika Çocuk"))) "Turkish" else "English")
                put("start", programme.start.toLocalTime().toString().take(5))
                put("stop", programme.stop.toLocalTime().toString().take(5))
            })
        }
    requestBody.put("epgDate", now.toLocalDate().toString())
    requestBody.put("epgWindowStart", now.toLocalTime().toString().take(5))
    requestBody.put("epgWindowHours", 0)
    requestBody.put("epgAvailable", programmes.length() > 0)
    requestBody.put("programmes", programmes)

    val connection = (URL(AI_ADVISER_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 12_000
        readTimeout = 75_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
    }

    try {
        connection.outputStream.use { it.write(requestBody.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Cloudflare adviser returned HTTP $code")
        val root = JSONObject(text)
        if (root.has("error")) error(root.optString("error", "AI request failed"))
        val answer = root.optString("answer").trim().takeIf { it.isNotBlank() }
            ?: error("AI returned no answer")
        val programmeSuggestions = mutableListOf<AdviserProgrammeSuggestion>()
        val programmeArray = root.optJSONArray("programmes") ?: JSONArray()
        for (i in 0 until programmeArray.length()) {
            val item = programmeArray.optJSONObject(i) ?: continue
            val programme = item.optString("programme").trim()
            val channel = item.optString("channel").trim()
            val reason = item.optString("reason").trim()
            if (programme.isBlank() || channel.isBlank()) continue
            programmeSuggestions += AdviserProgrammeSuggestion(programme, channel, reason)
        }
        AdviserQuestionAnswer(answer, programmeSuggestions)
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun AdviserStatusPill(kind: String) {
    val label = when (kind) {
        "keep" -> "KEEP"
        "later" -> "LOWER"
        "add" -> "ADD"
        else -> "REVIEW"
    }
    val background = when (kind) {
        "keep" -> Pink.copy(alpha = .16f)
        "later" -> Color(0xFFFFC46B).copy(alpha = .12f)
        "add" -> Lavender.copy(alpha = .15f)
        else -> Panel
    }
    val foreground = when (kind) {
        "keep" -> PinkSoft
        "later" -> Color(0xFFFFD18A)
        "add" -> Lavender
        else -> TextSecondary
    }
    Surface(shape = RoundedCornerShape(99.dp), color = background) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = .5.sp
        )
    }
}

private fun adviserChannelName(heading: String): String = heading.substringBefore("  •  ").trim()

@Composable
private fun AdviserChannelRow(recommendation: AdviserRecommendation) {
    val accent = if (recommendation.kind == "later") Amber else if (recommendation.kind == "keep") Mint else Lavender
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha=.08f), SurfaceHigh, SurfaceSoft)))
            .border(1.dp, accent.copy(alpha=.11f), RoundedCornerShape(22.dp)).padding(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha=.12f)), contentAlignment=Alignment.Center) {
            Icon(if(recommendation.kind=="later") Icons.Rounded.Schedule else Icons.Rounded.Star,null,tint=accent,modifier=Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(adviserChannelName(recommendation.heading), modifier=Modifier.weight(1f), fontWeight=FontWeight.Black, fontSize=14.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp)); AdviserStatusPill(recommendation.kind)
            }
            Spacer(Modifier.height(5.dp)); Text(recommendation.body,color=TextSecondary,fontSize=10.sp,lineHeight=15.sp)
        }
    }
}

@Composable
private fun AdviserAddResult(recommendation: AdviserRecommendation) {
    val emptyState = recommendation.heading.startsWith("No strong new channel", ignoreCase = true)
    val accent = if(emptyState) Mint else Lavender
    Row(
        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha=.08f),SurfaceHigh,SurfaceSoft)))
            .border(1.dp,accent.copy(alpha=.11f),RoundedCornerShape(22.dp)).padding(14.dp),
        verticalAlignment=Alignment.Top
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha=.12f)),contentAlignment=Alignment.Center) {
            Icon(if(emptyState) Icons.Rounded.StarBorder else Icons.Rounded.Tv,null,tint=accent,modifier=Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) {
            Text(if(emptyState) "Your lineup already covers this age well" else adviserChannelName(recommendation.heading),color=if(emptyState) TextPrimary else Lavender,fontWeight=FontWeight.Black,fontSize=13.sp)
            Spacer(Modifier.height(4.dp)); Text(recommendation.body,color=TextSecondary,fontSize=10.sp,lineHeight=15.sp)
        }
    }
}

private data class RecommendedTvSlot(
    val time: String,
    val partOfDay: String,
    val language: String,
    val channel: String,
    val note: String,
)

private fun recommendedTvSchedule(ageMonths: Int, channels: List<TvChannel>): List<RecommendedTvSlot> {
    val kids = channels.filter { it.group.equals("Kids", ignoreCase = true) }
    fun find(vararg names: String): String? = names.firstNotNullOfOrNull { wanted ->
        kids.firstOrNull { channelKey(it.id) == channelKey(wanted) || channelKey(it.name) == channelKey(wanted) }?.name
    }

    val englishPrimary = if (ageMonths < 18) {
        listOfNotNull(find("BabyFirst"), find("Duck TV"), find("CBeebies"), find("Kidoodle TV"))
    } else {
        listOfNotNull(find("CBeebies"), find("Kidoodle TV"), find("PBS KIDS"), find("Moonbug Kids"), find("Duck TV"), find("BabyFirst"))
    }.distinct()
    val turkishPrimary = listOfNotNull(find("TRT Çocuk"), find("Minika Çocuk")).distinct()

    if (englishPrimary.isEmpty() && turkishPrimary.isEmpty()) return emptyList()
    fun english(index: Int) = englishPrimary.getOrNull(index % maxOf(englishPrimary.size, 1)) ?: turkishPrimary.first()
    fun turkish(index: Int) = turkishPrimary.getOrNull(index % maxOf(turkishPrimary.size, 1)) ?: englishPrimary.first()

    val ageNote = when {
        ageMonths < 12 -> "Keep it calm and choose a simple, age-suitable programme."
        ageMonths < 24 -> "Prefer simple songs, words, repetition and easy-to-follow programmes."
        ageMonths < 36 -> "Good for simple vocabulary, songs, stories and repetition."
        else -> "Choose programmes with clear speech, stories and age-appropriate learning."
    }

    return listOf(
        RecommendedTvSlot("08:00", "Morning", "English", english(0), ageNote),
        RecommendedTvSlot("10:30", "Late morning / lunch", "Türkçe", turkish(0), ageNote),
        RecommendedTvSlot("13:00", "Afternoon", "English", english(1), ageNote),
        RecommendedTvSlot("15:30", "Early evening", "Türkçe", turkish(1), ageNote),
    )
}

@Composable
private fun RecommendedTvScheduleCard(ageMonths: Int, channels: List<TvChannel>) {
    val schedule = remember(ageMonths, channels) { recommendedTvSchedule(ageMonths, channels) }
    if (schedule.isEmpty()) return

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Recommended TV Schedule", color = PinkSoft, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Age-based English + Türkçe rotation • choose an age-suitable programme on the suggested channel",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(12.dp))
            schedule.forEachIndexed { index, slot ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Pink.copy(alpha = .11f)) {
                        Text(
                            if (slot.language == "Türkçe") "TR" else "EN",
                            color = PinkSoft,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(slot.time, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${slot.partOfDay} • ${slot.language}", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(slot.channel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(slot.note, color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
                if (index != schedule.lastIndex) Spacer(Modifier.height(11.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "The aim is regular exposure to both languages, not equal screen time. Talking, singing and reading together in English and Turkish still matter more than the TV schedule.",
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}


@Composable
private fun AgeSliderLabels() {
    // Keep the visual ruler deliberately uncluttered. The Slider itself is 0..60
    // months in one-month steps; these labels are anchors, not additional steps.
    // Material 3 draws the track between thumb centres (about 10dp in), so the
    // ruler uses that same inset. Minor six-month ticks remain visible without
    // cramming labels together at the baby end of the scale.
    val majorLabels = listOf(
        0 to "0m",
        6 to "6m",
        12 to "1y",
        24 to "2y",
        36 to "3y",
        48 to "4y",
        60 to "5y",
    )
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 10.dp)) {
            (0..60 step 6).forEach { month ->
                val dotSize = if (month % 12 == 0) 5.dp else 3.dp
                val x = (maxWidth - dotSize) * (month / 60f)
                Box(
                    Modifier.offset(x = x).size(dotSize).clip(CircleShape)
                        .background(if (month % 12 == 0) PinkSoft.copy(alpha=.72f) else TextTertiary.copy(alpha=.62f))
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 10.dp)) {
            majorLabels.forEach { (month, label) ->
                val labelWidth = 26.dp
                val centre = maxWidth * (month / 60f)
                val x = (centre - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)
                Text(label, color = TextSecondary, fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center,
                    maxLines = 1, modifier = Modifier.width(labelWidth).offset(x = x))
            }
        }
    }
}

private object AiSessionCache {
    var ageMonths: Int = 7
    var recommendations: List<AdviserRecommendation>? = null
    var questionAnswer: AdviserQuestionAnswer? = null
}

data class SharedScheduleEntry(val time: String, val stop: String = "", val title: String, val channel: String, val language: String, val nextTitle: String = "", val nextStart: String = "", val nextStop: String = "")

private fun scheduleSuggestionLanguage(entries: List<SharedScheduleEntry>): String? = when (entries.lastOrNull()?.language?.lowercase(Locale.ROOT)) {
    "turkish", "türkçe" -> "English"
    "english" -> "Türkçe"
    else -> null
}

private fun scheduleToJson(entries: List<SharedScheduleEntry>): JSONArray = JSONArray().apply {
    entries.forEach { e -> put(JSONObject().apply { put("time",e.time); put("stop",e.stop); put("title",e.title); put("channel",e.channel); put("language",e.language); put("nextTitle",e.nextTitle); put("nextStart",e.nextStart); put("nextStop",e.nextStop) }) }
}
private fun scheduleFromJson(raw: String): List<SharedScheduleEntry> = runCatching {
    val a=JSONArray(raw); (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o -> SharedScheduleEntry(o.optString("time"),o.optString("stop"),o.optString("title"),o.optString("channel"),o.optString("language"),o.optString("nextTitle"),o.optString("nextStart"),o.optString("nextStop")) } }
}.getOrDefault(emptyList())

private suspend fun syncSchedule(code: String, entries: List<SharedScheduleEntry>?): List<SharedScheduleEntry> = withContext(Dispatchers.IO) {
    val clean=code.trim().uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(12)
    require(clean.length >= 6) { "Pairing code must be at least 6 characters" }
    val url=URL("https://umay-tv-ai.matthewwood406.workers.dev/api/schedule?household=$clean")
    val c=(url.openConnection() as HttpURLConnection).apply { requestMethod=if(entries==null) "GET" else "PUT"; connectTimeout=12000; readTimeout=20000; setRequestProperty("Accept","application/json") }
    if(entries!=null){ c.doOutput=true; c.setRequestProperty("Content-Type","application/json; charset=utf-8"); val body=JSONObject().put("entries",scheduleToJson(entries)); c.outputStream.use{it.write(body.toString().toByteArray())} }
    val codeHttp=c.responseCode; val text=(if(codeHttp in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty(); c.disconnect()
    if(codeHttp !in 200..299) error("Schedule sync returned HTTP $codeHttp")
    val response = JSONObject(text)
    val a=response.optJSONArray("entries")?:JSONArray()
    if (entries == null && a.length() > 0) {
        val updatedAt = response.optString("updatedAt")
        val remoteDate = runCatching { java.time.Instant.parse(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        if (remoteDate != null && remoteDate != LocalDate.now()) {
            return@withContext syncSchedule(clean, emptyList())
        }
    }
    scheduleFromJson(a.toString())
}


private fun scheduleSharedFollowUps(
    context: Context,
    household: String,
    memberName: String,
    entries: List<SharedScheduleEntry>,
) {
    if (household.trim().length < 6) return
    if (!SchedulePolicy.isScheduleForToday(context)) return
    val now = ZonedDateTime.now()
    val endTime = SchedulePolicy.endTime(context)
    entries.forEach { entry ->
        if (entry.title == "TV switched off") return@forEach
        val stopTime = runCatching { LocalTime.parse(entry.stop) }.getOrNull() ?: return@forEach
        if (!stopTime.isBefore(endTime)) return@forEach
        val stop = now.with(stopTime).withSecond(0).withNano(0)
        if (!stop.isAfter(now)) return@forEach
        ScheduleFollowUpScheduler.schedule(
            context = context,
            household = household,
            memberName = memberName.ifBlank { "Someone" },
            title = entry.title,
            channel = entry.channel,
            language = entry.language,
            stop = stop,
            nextTitle = entry.nextTitle,
            nextStart = entry.nextStart,
            nextStop = entry.nextStop,
        )
    }
}

@Composable
private fun SharedScheduleView(guide: GuideData, channels: List<TvChannel>) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()
    var age by rememberSaveable { mutableIntStateOf(7) }
    var pairing by rememberSaveable { mutableStateOf(prefs.getString(PREF_HOUSEHOLD_CODE, "") ?: "") }
    var memberName by rememberSaveable { mutableStateOf(prefs.getString(PREF_HOUSEHOLD_MEMBER, "") ?: "") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    val initialScheduleIsToday = prefs.getString(SchedulePolicy.PREF_DATE, null) == SchedulePolicy.todayKey()
    var entries by remember { mutableStateOf(if (initialScheduleIsToday) scheduleFromJson(prefs.getString(PREF_SCHEDULE_JSON, "[]") ?: "[]") else emptyList()) }
    var scheduleEndTime by rememberSaveable { mutableStateOf(prefs.getString(SchedulePolicy.PREF_END_TIME, SchedulePolicy.DEFAULT_END_TIME) ?: SchedulePolicy.DEFAULT_END_TIME) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var chooseNowPrompt by remember { mutableStateOf(prefs.getBoolean(PREF_SCHEDULE_CHOOSE_NOW, false)) }
    var showAllRecommendations by rememberSaveable { mutableStateOf(false) }
    var householdExpanded by rememberSaveable { mutableStateOf(false) }
    var livePicksExpanded by rememberSaveable { mutableStateOf(false) }
    val joined = pairing.trim().length >= 6
    val suggestion = scheduleSuggestionLanguage(entries)
    val nowKids = remember(guide, channels) { onNowKidsProgrammes(guide, channels) }
    val recommended = remember(age, channels) { recommendedTvSchedule(age, channels) }

    fun saveLocal(newEntries: List<SharedScheduleEntry>) {
        entries = newEntries
        prefs.edit().putString(PREF_SCHEDULE_JSON, scheduleToJson(newEntries).toString()).putString(SchedulePolicy.PREF_DATE, SchedulePolicy.todayKey()).apply()
    }

    fun saveMember(name: String) {
        memberName = name
        prefs.edit().putString(PREF_HOUSEHOLD_MEMBER, name).apply()
        if (joined) scheduleSharedFollowUps(context, pairing, name, entries)
    }

    fun chooseEndTime() {
        val current = runCatching { LocalTime.parse(scheduleEndTime) }.getOrDefault(LocalTime.of(17, 0))
        android.app.TimePickerDialog(
            context,
            { _, hour, minute ->
                val chosen = LocalTime.of(hour, minute).toString().take(5)
                scheduleEndTime = chosen
                prefs.edit().putString(SchedulePolicy.PREF_END_TIME, chosen).apply()
                if (!LocalTime.now().isBefore(LocalTime.of(hour, minute)) && entries.any { it.title != "TV switched off" }) {
                    context.sendBroadcast(Intent(context, ScheduleFollowUpReceiver::class.java).apply { action = ScheduleFollowUpReceiver.ACTION_AUTO_TV_OFF })
                }
                ScheduleDayScheduler.schedule(context)
                scheduleSharedFollowUps(context, pairing, memberName, entries)
                status = "Viewing end time set to $chosen"
            },
            current.hour,
            current.minute,
            true,
        ).show()
    }

    fun syncRemote(newEntries: List<SharedScheduleEntry>) {
        if (!joined) return
        scope.launch { runCatching { syncSchedule(pairing, newEntries) } }
    }

    fun joinHousehold(code: String) {
        val clean = code.trim().uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(12)
        if (clean.length < 6) { status = "Enter a valid household code"; return }
        busy = true
        status = "Joining household…"
        scope.launch {
            runCatching { syncSchedule(clean, null) }
                .onSuccess {
                    pairing = clean
                    prefs.edit().putString(PREF_HOUSEHOLD_CODE, clean).apply()
                    saveLocal(it)
                    scheduleSharedFollowUps(context, clean, memberName, it)
                    joinCode = ""
                    status = "Household joined"
                }
                .onFailure { status = it.message ?: "Could not join household" }
            busy = false
        }
    }

    fun createHousehold() {
        val code = (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        busy = true
        status = "Creating household…"
        scope.launch {
            runCatching { syncSchedule(code, entries) }
                .onSuccess {
                    pairing = code
                    prefs.edit().putString(PREF_HOUSEHOLD_CODE, code).apply()
                    scheduleSharedFollowUps(context, code, memberName, entries)
                    status = "Household created"
                }
                .onFailure { status = it.message ?: "Could not create household" }
            busy = false
        }
    }

    fun addProgramme(p: Programme) {
        val ch = channels.firstOrNull { channelKey(it.id) == channelKey(p.channelId) || channelKey(it.name) == channelKey(p.channelId) }
        val channelName = ch?.name ?: p.channelId
        val lang = if (channelName in listOf("TRT Çocuk", "Minika Çocuk")) "Türkçe" else "English"
        val next = guide.programmes
            .asSequence()
            .filter { channelKey(it.channelId) == channelKey(p.channelId) && !it.start.isBefore(p.stop.minusMinutes(1)) }
            .minByOrNull { it.start }
        val entry = SharedScheduleEntry(
            time = p.start.toLocalTime().toString().take(5),
            stop = p.stop.toLocalTime().toString().take(5),
            title = p.title,
            channel = channelName,
            language = lang,
            nextTitle = next?.title.orEmpty(),
            nextStart = next?.start?.toLocalTime()?.toString()?.take(5).orEmpty(),
            nextStop = next?.stop?.toLocalTime()?.toString()?.take(5).orEmpty(),
        )
        val newEntries = entries + entry
        saveLocal(newEntries)
        syncRemote(newEntries)
        ScheduleFollowUpScheduler.schedule(
            context = context,
            household = pairing,
            memberName = memberName.ifBlank { "Someone" },
            title = p.title,
            channel = channelName,
            language = lang,
            stop = p.stop,
            nextTitle = next?.title.orEmpty(),
            nextStart = next?.start?.toLocalTime()?.toString()?.take(5).orEmpty(),
            nextStop = next?.stop?.toLocalTime()?.toString()?.take(5).orEmpty(),
        )
        status = "Added ${p.title}"
    }

    LaunchedEffect(pairing) {
        ScheduleDayScheduler.schedule(context)
        if (!initialScheduleIsToday) {
            // Clear the stale local day immediately, but never overwrite a household
            // schedule until we have checked whether the other phone already has today's data.
            saveLocal(emptyList())
        }
        if (pairing.trim().length >= 6) {
            runCatching { syncSchedule(pairing, null) }
                .onSuccess {
                    saveLocal(it)
                    scheduleSharedFollowUps(context, pairing, memberName, it)
                }
        }
    }

    if (chooseNowPrompt) {
        AlertDialog(
            onDismissRequest = {
                chooseNowPrompt = false
                prefs.edit().remove(PREF_SCHEDULE_CHOOSE_NOW).apply()
            },
            title = { Text("Which kids channel did you change to?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Choose the kids channel now on the TV. The list shows what is currently playing on each channel.", color = TextSecondary, fontSize = 12.sp)
                    if (nowKids.isEmpty()) {
                        Text("No kids channels currently have a live programme in the EPG.", color = PinkSoft, fontWeight = FontWeight.SemiBold)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(nowKids.sortedBy { p -> channels.firstOrNull { channelKey(it.id) == channelKey(p.channelId) || channelKey(it.name) == channelKey(p.channelId) }?.name ?: p.channelId }) { p ->
                                val ch = channels.firstOrNull { channelKey(it.id) == channelKey(p.channelId) || channelKey(it.name) == channelKey(p.channelId) }
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        addProgramme(p)
                                        chooseNowPrompt = false
                                        prefs.edit().remove(PREF_SCHEDULE_CHOOSE_NOW).apply()
                                    },
                                    color = Panel2,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val logo = ch?.icon
                                        Box(
                                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                                .background(Panel2.copy(alpha = .88f))
                                                .border(1.dp, Hairline, RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (!logo.isNullOrBlank()) AsyncImage(logo, ch?.name ?: p.channelId, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit)
                                            else Text((ch?.name ?: p.channelId).take(2).uppercase(Locale.ROOT), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(ch?.name ?: p.channelId, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                            Text(p.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${p.start.toLocalTime().toString().take(5)}–${p.stop.toLocalTime().toString().take(5)}", color = TextSecondary, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    chooseNowPrompt = false
                    prefs.edit().remove(PREF_SCHEDULE_CHOOSE_NOW).apply()
                }) { Text("Not now") }
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Exit household?") },
            text = { Text("This removes the household code and cached schedule from this phone only. The shared household remains available to the other phone.") },
            confirmButton = {
                TextButton(onClick = {
                    pairing = ""; joinCode = ""; entries = emptyList()
                    prefs.edit().remove(PREF_HOUSEHOLD_CODE).remove(PREF_SCHEDULE_JSON).apply()
                    status = "You have left the household"
                    confirmExit = false
                }) { Text("Exit household") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Lavender.copy(alpha=.12f), SurfaceHigh, SurfaceSoft)))
                    .border(1.dp, Lavender.copy(alpha=.15f), RoundedCornerShape(30.dp)).padding(18.dp)
            ) {
                Box(Modifier.align(Alignment.TopEnd).size(170.dp).background(Brush.radialGradient(listOf(Lavender.copy(alpha=.15f), Color.Transparent))))
                Column(verticalArrangement=Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Pink.copy(alpha=.22f), Lavender.copy(alpha=.17f)))), contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.Schedule, null, tint=PinkSoft, modifier=Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("UMAY’S VIEWING PROFILE", color=Lavender, fontSize=8.sp, fontWeight=FontWeight.Black, letterSpacing=1.sp)
                            Text("Age-aware, bilingual rhythm", fontSize=20.sp, fontWeight=FontWeight.Black, letterSpacing=(-.45).sp)
                        }
                        CompactPill(formatAge(age), Pink)
                    }
                    Slider(
                        value=ageToSliderPosition(age),
                        onValueChange={ age=sliderPositionToAge(it) },
                        valueRange=0f..60f,
                        steps=59,
                        colors=SliderDefaults.colors(thumbColor=TextPrimary, activeTrackColor=Pink, inactiveTrackColor=Color.White.copy(alpha=.08f))
                    )
                    AgeSliderLabels()
                    Text("1 month per step • English + Türkçe", color=TextTertiary, fontSize=9.sp)
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(SurfaceSoft.copy(alpha=.88f))
                    .border(1.dp, Hairline, RoundedCornerShape(22.dp))
                    .clickable { chooseEndTime() }
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Cyan.copy(alpha=.11f)), contentAlignment=Alignment.Center) {
                    Icon(Icons.Rounded.Schedule, null, tint=Cyan, modifier=Modifier.size(21.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Viewing ends at $scheduleEndTime", fontWeight=FontWeight.Black, fontSize=14.sp)
                    Text("At this time TV switched off is added automatically. Tap to change it.", color=TextSecondary, fontSize=9.sp, lineHeight=13.sp)
                }
                Icon(Icons.Rounded.KeyboardArrowRight, null, tint=TextSecondary)
            }
        }

        item {
            PremiumSectionHeader(
                eyebrow="TODAY’S RHYTHM",
                title="Recommended schedule",
                action=if (recommended.size > 3) { if (showAllRecommendations) "Show less" else "Show all" } else null
            ) { showAllRecommendations = !showAllRecommendations }
            Spacer(Modifier.height(4.dp))
            Text("A light guide for what to put on — not a rigid timetable.", color=TextSecondary, fontSize=10.sp)
        }

        itemsIndexed(if (showAllRecommendations) recommended else recommended.take(3)) { index, slot ->
            val slotChannel = channels.firstOrNull { channelKey(it.name)==channelKey(slot.channel) || channelKey(it.id)==channelKey(slot.channel) }
            val currentOnSlot = nowKids.firstOrNull { p ->
                channelKey(p.channelId)==channelKey(slotChannel?.id ?: slot.channel) || channelKey(p.channelId)==channelKey(slotChannel?.name ?: slot.channel)
            }
            val accent = if(slot.language=="Türkçe") Lavender else Pink
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha=.10f), SurfaceHigh, SurfaceSoft)))
                    .border(1.dp, accent.copy(alpha=.14f), RoundedCornerShape(24.dp)).padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(82.dp)) {
                            Text(slot.time, fontSize=17.sp, fontWeight=FontWeight.Black, letterSpacing=(-.3).sp)
                            Text(slot.partOfDay, color=TextSecondary, fontSize=9.sp, lineHeight=12.sp)
                        }
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Panel2).border(1.dp, Hairline, RoundedCornerShape(15.dp)), contentAlignment=Alignment.Center) {
                            if(!slotChannel?.icon.isNullOrBlank()) AsyncImage(slotChannel?.icon, slot.channel, Modifier.fillMaxSize().padding(5.dp), contentScale=ContentScale.Fit)
                            else Text(slot.channel.take(2).uppercase(Locale.ROOT), color=TextPrimary, fontWeight=FontWeight.Black, fontSize=10.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(slot.channel, fontWeight=FontWeight.Black, fontSize=14.sp, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
                                Spacer(Modifier.width(6.dp)); CompactPill(if(slot.language=="Türkçe") "TR" else "EN", accent)
                            }
                            Text(slot.note, color=TextSecondary, fontSize=9.sp, lineHeight=13.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
                        }
                        if(currentOnSlot!=null) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(accent).clickable { addProgramme(currentOnSlot) },
                                contentAlignment=Alignment.Center
                            ) { Text("+", color=Color(0xFF170514), fontWeight=FontWeight.Black, fontSize=21.sp) }
                        }
                    }
                    if (currentOnSlot != null) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha=.035f)).padding(horizontal=10.dp, vertical=8.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(6.dp).background(Mint, CircleShape)); Spacer(Modifier.width(7.dp))
                            Text("On now", color=Mint, fontSize=8.sp, fontWeight=FontWeight.Black, letterSpacing=.6.sp)
                            Spacer(Modifier.width(7.dp))
                            Text(currentOnSlot.title, color=TextPrimary, fontSize=10.sp, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
                        }
                    }
                }
            }
        }


        item {
            PremiumSectionHeader(
                eyebrow="HOUSEHOLD",
                title=if (joined) "Matt + Sev connected" else "Connect both phones",
                action=if (householdExpanded) "Close" else "Manage"
            ) { householdExpanded = !householdExpanded }
            AnimatedVisibility(visible = householdExpanded) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF161429), Color(0xFF10182A), Color(0xFF08101F))))
                        .border(1.dp, Pink.copy(alpha=.14f), RoundedCornerShape(30.dp))
                ) {
                Box(Modifier.align(Alignment.TopEnd).size(150.dp).background(Brush.radialGradient(listOf(Pink.copy(alpha=.16f),Color.Transparent))))
                Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(Pink.copy(alpha=.12f)),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.Home,null,tint=PinkSoft,modifier=Modifier.size(22.dp)) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) { Text("SHARED HOUSEHOLD",color=PinkSoft,fontSize=9.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp); Text("Matt + Sev",fontSize=19.sp,fontWeight=FontWeight.Black) }
                        if(joined) CompactPill("CONNECTED",Mint)
                    }
                    if(joined) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha=.035f))
                                .border(1.dp,Hairline,RoundedCornerShape(20.dp)).padding(14.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) { Text("HOUSEHOLD CODE",color=TextSecondary,fontSize=8.sp,fontWeight=FontWeight.Black,letterSpacing=.8.sp); Text(pairing,fontSize=20.sp,fontWeight=FontWeight.Black,letterSpacing=1.8.sp) }
                            Column(horizontalAlignment=Alignment.End) { Text("THIS PHONE",color=TextSecondary,fontSize=8.sp,fontWeight=FontWeight.Black); Text(memberName.ifBlank { "Choose" },color=if(memberName.isBlank()) Amber else Mint,fontWeight=FontWeight.Bold,fontSize=11.sp) }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                            listOf("Matt","Sev").forEach { name -> PremiumChoiceChip(name,memberName==name,if(name=="Matt") Cyan else Pink) { saveMember(name) } }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick={
                                    busy=true; status="Syncing…"
                                    scope.launch {
                                        runCatching { syncSchedule(pairing,null) }
                                            .onSuccess { saveLocal(it); scheduleSharedFollowUps(context,pairing,memberName,it); status="Synced" }
                                            .onFailure { status=it.message }
                                        busy=false
                                    }
                                },
                                enabled=!busy, shape=RoundedCornerShape(16.dp), colors=ButtonDefaults.buttonColors(containerColor=Pink,contentColor=Color(0xFF21000F)),
                                modifier=Modifier.weight(1f)
                            ) { Icon(Icons.Rounded.Refresh,null,Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("Sync",fontWeight=FontWeight.Black) }
                            OutlinedButton(onClick={ confirmExit=true },enabled=!busy,shape=RoundedCornerShape(16.dp),modifier=Modifier.weight(1f)) { Text("Leave") }
                        }
                    } else {
                        Text("Create the household once, then use the same code on the other phone.",color=TextSecondary,fontSize=10.sp,lineHeight=15.sp)
                        OutlinedTextField(
                            value=joinCode,
                            onValueChange={ joinCode=it.uppercase(Locale.ROOT).filter { ch -> ch.isLetterOrDigit() }.take(12) },
                            label={ Text("Household code") }, singleLine=true, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(18.dp),
                            colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Color.White.copy(alpha=.025f),unfocusedContainerColor=Color.White.copy(alpha=.025f),focusedBorderColor=Pink.copy(alpha=.45f),unfocusedBorderColor=Hairline)
                        )
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick={ joinHousehold(joinCode) },enabled=!busy && joinCode.length>=6,shape=RoundedCornerShape(16.dp),modifier=Modifier.weight(1f)) { Text("Join",fontWeight=FontWeight.Black) }
                            OutlinedButton(onClick={ createHousehold() },enabled=!busy,shape=RoundedCornerShape(16.dp),modifier=Modifier.weight(1f)) { Text("Create new") }
                        }
                    }
                    status?.let { Text(it,color=TextSecondary,fontSize=9.sp) }
                    }
                }
            }
        }

        if (LocalTime.now().isBefore(runCatching { LocalTime.parse(scheduleEndTime) }.getOrDefault(LocalTime.of(17, 0)))) {
            item {
                PremiumSectionHeader(
                    eyebrow="LIVE PICKS",
                    title="Add something on now",
                    action=if (livePicksExpanded) "Close" else "Browse"
                ) { livePicksExpanded = !livePicksExpanded }
                AnimatedVisibility(visible = livePicksExpanded) {
                    Column(Modifier.padding(top = 8.dp)) {
                        suggestion?.let { Text("Language balance suggestion: $it next — optional.", color=PinkSoft, fontSize=10.sp, modifier=Modifier.padding(bottom=8.dp)) }
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp), contentPadding=PaddingValues(end=4.dp)) {
                    items(nowKids) { p ->
                        val ch=channels.firstOrNull { channelKey(it.id)==channelKey(p.channelId) || channelKey(it.name)==channelKey(p.channelId) }
                        val lang=if(ch?.name in listOf("TRT Çocuk","Minika Çocuk")) "Türkçe" else "English"
                        val accent=if(lang=="Türkçe") Lavender else Pink
                        Box(
                            Modifier.width(210.dp).clip(RoundedCornerShape(24.dp))
                                .background(Brush.linearGradient(listOf(accent.copy(alpha=.09f),Color(0xFF101A2F),Color(0xFF08101F))))
                                .border(1.dp,accent.copy(alpha=.13f),RoundedCornerShape(24.dp)).clickable { addProgramme(p) }
                        ) {
                            Column {
                                Box(Modifier.fillMaxWidth().height(72.dp).background(Panel2.copy(alpha=.88f)),contentAlignment=Alignment.Center) {
                                    val logo=ch?.icon
                                    if(!logo.isNullOrBlank()) AsyncImage(logo,ch.name,Modifier.fillMaxSize().padding(9.dp),contentScale=ContentScale.Fit)
                                    else Text(ch?.name ?: p.channelId,color=TextPrimary,fontWeight=FontWeight.Black,fontSize=13.sp)
                                }
                                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        CompactPill(if(lang=="Türkçe") "TR" else "EN",accent)
                                        Spacer(Modifier.weight(1f))
                                        Surface(color=accent,shape=CircleShape) { Text("+",color=Color(0xFF170514),fontWeight=FontWeight.Black,fontSize=18.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=4.dp)) }
                                    }
                                    Text(p.title,fontWeight=FontWeight.Black,fontSize=15.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text(ch?.name ?: p.channelId,color=TextSecondary,fontSize=10.sp,maxLines=1)
                                    Text("${p.start.toLocalTime().toString().take(5)}–${p.stop.toLocalTime().toString().take(5)}",color=TextSecondary,fontSize=9.sp)
                                }
                            }
                        }
                    }
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF101A2F),Color(0xFF08101F))))
                        .border(1.dp,Hairline,RoundedCornerShape(24.dp)).padding(17.dp),
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(Cyan.copy(alpha=.12f)),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.Tv,null,tint=Cyan,modifier=Modifier.size(22.dp)) }
                    Spacer(Modifier.width(11.dp)); Column { Text("Matt & Sev TV time",fontWeight=FontWeight.Black,fontSize=17.sp); Text("Umay recommendations are finished for today • viewing ended at $scheduleEndTime.",color=TextSecondary,fontSize=10.sp) }
                }
            }
        }

        item { PremiumSectionHeader(eyebrow="HOUSEHOLD", title="Today’s shared picks", action=if(joined) "Synced" else "Local") {} }
        if (entries.isEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceSoft.copy(alpha=.9f))
                        .border(1.dp, Hairline, RoundedCornerShape(22.dp)).padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Pink.copy(alpha=.12f)), contentAlignment=Alignment.Center) {
                        Icon(Icons.Rounded.Schedule, null, tint=PinkSoft, modifier=Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nothing scheduled yet", fontWeight=FontWeight.Black, fontSize=14.sp)
                        Text("Add something from the live picks above and both phones will see it here.", color=TextSecondary, fontSize=9.sp, lineHeight=13.sp)
                    }
                }
            }
        } else {
            itemsIndexed(entries) { i, e ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel2.copy(alpha=.74f)).border(1.dp,Hairline,RoundedCornerShape(22.dp))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Pink.copy(alpha=.13f), shape=RoundedCornerShape(12.dp)) {
                            Text(e.time, color=PinkSoft, fontWeight=FontWeight.Black, modifier=Modifier.padding(horizontal=9.dp, vertical=7.dp), fontSize=11.sp)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, fontWeight = FontWeight.Black)
                            Text("${e.channel} • ${e.language}${if (e.stop.isNotBlank()) " • until ${e.stop}" else ""}", color = TextSecondary, fontSize = 10.sp)
                        }
                        IconButton(onClick = {
                            val n = entries.toMutableList().also { it.removeAt(i) }
                            saveLocal(n); syncRemote(n)
                        }) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelAdviserView(
    guide: GuideData,
    channels: List<TvChannel>,
) {
    var ageMonths by rememberSaveable { mutableIntStateOf(AiSessionCache.ageMonths) }
    var aiRecommendations by remember { mutableStateOf<List<AdviserRecommendation>?>(AiSessionCache.recommendations) }
    var isThinking by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var question by rememberSaveable { mutableStateOf("") }
    var questionAnswer by remember { mutableStateOf<AdviserQuestionAnswer?>(AiSessionCache.questionAnswer) }
    var isAnsweringQuestion by remember { mutableStateOf(false) }
    var questionError by remember { mutableStateOf<String?>(null) }
    var showAllChannelFit by rememberSaveable { mutableStateOf(false) }
    var askExpanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val offlineFallback = remember(ageMonths, guide, channels) { channelAdviserRecommendations(ageMonths, guide, channels) }
    val recommendations = aiRecommendations ?: offlineFallback
    val summaryRecommendation = recommendations.firstOrNull { it.heading == "AI recommendation" || it.heading == "Offline fallback" }
    val focusRecommendation = recommendations.firstOrNull { it.heading == "Best focus for this age" }
    val currentChannelRecommendations = recommendations.filter { it.kind != "add" && it !== summaryRecommendation && it !== focusRecommendation }
    val additions = recommendations.filter { it.kind == "add" }
    val nowKids = remember(guide, channels) { onNowKidsProgrammes(guide, channels) }
    val kidsEpgAvailable = nowKids.isNotEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceSoft)))
                    .border(1.dp, Hairline, RoundedCornerShape(28.dp)).padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Lavender.copy(alpha=.14f)), contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.Star, null, tint=Lavender, modifier=Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("UMAY AI", color=Lavender, fontSize=8.sp, fontWeight=FontWeight.Black, letterSpacing=1.sp)
                            Text("Smarter viewing picks", fontSize=18.sp, fontWeight=FontWeight.Black, letterSpacing=(-.35).sp)
                        }
                        CompactPill(if (kidsEpgAvailable) "LIVE" else "OFFLINE", if (kidsEpgAvailable) Mint else Amber)
                    }
                    HorizontalDivider(color=Hairline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("AGE TO ADVISE FOR", color=PinkSoft, fontWeight=FontWeight.Black, fontSize=8.sp, letterSpacing=.9.sp)
                            Text(formatAge(ageMonths), fontSize=26.sp, fontWeight=FontWeight.Black, letterSpacing=(-.65).sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            CompactPill(if (aiRecommendations != null) "AI READY" else "PREVIEW", if (aiRecommendations != null) Mint else Lavender)
                            Spacer(Modifier.height(5.dp))
                            Text("1 month per step", color=TextTertiary, fontSize=8.sp)
                        }
                    }
                    Slider(
                        value = ageToSliderPosition(ageMonths),
                        onValueChange = {
                            val newAge = sliderPositionToAge(it)
                            if (newAge != ageMonths) {
                                ageMonths = newAge
                                AiSessionCache.ageMonths = newAge
                                aiRecommendations = null
                                AiSessionCache.recommendations = null
                            }
                            aiError = null
                        },
                        valueRange = 0f..60f,
                        steps = 59,
                        colors = SliderDefaults.colors(thumbColor=TextPrimary, activeTrackColor=Pink, inactiveTrackColor=Color.White.copy(alpha=.08f)),
                    )
                    AgeSliderLabels()
                    Button(
                        onClick = {
                            if (isThinking) return@Button
                            isThinking = true
                            aiError = null
                            scope.launch {
                                runCatching { fetchCloudflareAdviserRecommendations(ageMonths, guide, channels) }
                                    .onSuccess { aiRecommendations = it; AiSessionCache.recommendations = it }
                                    .onFailure { error ->
                                        Log.e("UmayTVGuide-AI", "Dashboard adviser failed", error)
                                        aiError = if (aiRecommendations != null) "Couldn't refresh just now. Keeping the last result." else "AI is taking longer just now. Showing guide-based advice instead."
                                    }
                                isThinking = false
                            }
                        },
                        enabled=!isThinking,
                        modifier=Modifier.fillMaxWidth().height(50.dp),
                        shape=RoundedCornerShape(17.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=Pink, contentColor=Color(0xFF25000F)),
                    ) {
                        if (isThinking) {
                            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth=2.dp, color=Color(0xFF25000F))
                            Spacer(Modifier.width(8.dp)); Text("Thinking…", fontWeight=FontWeight.Black)
                        } else {
                            Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if(aiRecommendations==null) "Ask AI" else "Refresh advice", fontWeight=FontWeight.Black)
                        }
                    }
                    aiError?.let { Text(it, color=TextSecondary, fontSize=10.sp, lineHeight=14.sp) }
                }
            }
        }

        summaryRecommendation?.let { summary ->
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Lavender.copy(alpha=.13f), Color(0xFF101A30), Color(0xFF09111F))))
                        .border(1.dp, Lavender.copy(alpha=.14f), RoundedCornerShape(28.dp)).padding(18.dp)
                ) {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(aiRecommendations!=null) "AI VERDICT" else "ON-DEVICE PREVIEW", color=Lavender, fontSize=9.sp, fontWeight=FontWeight.Black, letterSpacing=1.sp)
                            Spacer(Modifier.weight(1f)); CompactPill(if(ageMonths<24) "$ageMonths MO" else "${ageMonths/12} YR", Lavender)
                        }
                        Text(summary.body, color=TextPrimary, fontSize=17.sp, lineHeight=23.sp, fontWeight=FontWeight.SemiBold)
                        focusRecommendation?.let { focus ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.035f)).padding(12.dp), verticalAlignment=Alignment.Top) {
                                Text("FOCUS", color=PinkSoft, fontSize=9.sp, fontWeight=FontWeight.Black); Spacer(Modifier.width(10.dp))
                                Text(focus.body, color=TextSecondary, fontSize=11.sp, lineHeight=16.sp, modifier=Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (currentChannelRecommendations.isNotEmpty()) {
            item {
                PremiumSectionHeader(
                    eyebrow="CURRENT LINEUP",
                    title="Channel fit",
                    action=if (currentChannelRecommendations.size > 3) { if (showAllChannelFit) "Show less" else "Show all" } else null
                ) { showAllChannelFit = !showAllChannelFit }
            }
            items(if (showAllChannelFit) currentChannelRecommendations else currentChannelRecommendations.take(3)) { AdviserChannelRow(it) }
        }

        item {
            PremiumSectionHeader(
                eyebrow="ASK UMAY AI",
                title="Ask a question",
                action=if (askExpanded) "Close" else "Open"
            ) { askExpanded = !askExpanded }
            AnimatedVisibility(visible = askExpanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    PolishedSection(title="Ask a question", subtitle="Channels, programmes or a future age", accent=Lavender) {
                OutlinedTextField(
                    value=question,
                    onValueChange={ question=it.take(500); questionError=null },
                    modifier=Modifier.fillMaxWidth(),
                    placeholder={ Text("e.g. What should we add at 9 months?", fontSize=11.sp) },
                    minLines=2, maxLines=4,
                    shape=RoundedCornerShape(18.dp),
                    colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Color.White.copy(alpha=.025f), unfocusedContainerColor=Color.White.copy(alpha=.025f), focusedBorderColor=Lavender.copy(alpha=.45f), unfocusedBorderColor=Hairline),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick={
                        if(isAnsweringQuestion || question.isBlank()) return@Button
                        isAnsweringQuestion=true; questionError=null
                        scope.launch {
                            runCatching { fetchCloudflareAdviserAnswer(ageMonths, question, guide, channels) }
                                .onSuccess { questionAnswer=it; AiSessionCache.questionAnswer=it }
                                .onFailure { error -> Log.e("UmayTVGuide-AI", "Question adviser failed", error); questionError="Couldn't get an AI answer just now. Please try again in a moment." }
                            isAnsweringQuestion=false
                        }
                    },
                    enabled=!isAnsweringQuestion && question.isNotBlank(),
                    modifier=Modifier.align(Alignment.End),
                    shape=RoundedCornerShape(16.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=Lavender, contentColor=Color(0xFF120A2A)),
                ) {
                    if(isAnsweringQuestion) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth=2.dp, color=Color(0xFF120A2A)) else Icon(Icons.Rounded.Star, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp)); Text(if(isAnsweringQuestion) "Thinking…" else "Ask", fontWeight=FontWeight.Black)
                }
                questionAnswer?.let { response ->
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Lavender.copy(alpha=.08f)).border(1.dp,Lavender.copy(alpha=.12f),RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Text("AI ANSWER", color=Lavender, fontSize=9.sp, fontWeight=FontWeight.Black, letterSpacing=.8.sp)
                        Spacer(Modifier.height(5.dp)); Text(response.answer, color=TextPrimary, fontSize=12.sp, lineHeight=18.sp)
                        if(response.programmes.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp)); Text("PROGRAMMES TO TRY", color=PinkSoft, fontSize=9.sp, fontWeight=FontWeight.Black)
                            response.programmes.forEach { suggestion ->
                                Spacer(Modifier.height(9.dp))
                                Row(verticalAlignment=Alignment.Top) {
                                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Pink.copy(alpha=.12f)), contentAlignment=Alignment.Center) { Icon(Icons.Rounded.PlayArrow,null,tint=PinkSoft,modifier=Modifier.size(16.dp)) }
                                    Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) {
                                        Text(suggestion.programme,fontWeight=FontWeight.Bold,fontSize=12.sp); Text("${suggestion.channel} • ${suggestion.reason}",color=TextSecondary,fontSize=10.sp,lineHeight=14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                questionError?.let { Text(it, color=TextSecondary, fontSize=10.sp, modifier=Modifier.padding(top=7.dp)) }
                    }
                }
            }
        }

        if (additions.isNotEmpty()) {
            item { PremiumSectionHeader(eyebrow="DISCOVER", title="Possible additions") }
            items(additions.take(3)) { AdviserAddResult(it) }
        }

        item {
            Text(
                "AI uses the selected age, your channel lineup and programmes currently airing on Kids channels when EPG is available. No child profile or date of birth is sent, and AI never edits channels.json automatically.",
                color=TextSecondary, fontSize=9.sp, lineHeight=14.sp, modifier=Modifier.padding(horizontal=4.dp, vertical=4.dp)
            )
        }
    }
}

@Composable
private fun SettingsView(
    reminderMode: ReminderMode,
    onReminderMode: (ReminderMode) -> Unit,
    use24Hour: Boolean,
    onUse24Hour: (Boolean) -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: (Boolean) -> Unit,
    defaultSection: AppSection,
    onDefaultSection: (AppSection) -> Unit,
    channelConfig: List<ChannelConfig>,
    guide: GuideData,
    lastUpdated: LocalTime?,
    use24HourForLabel: Boolean,
) {
    var default by remember(defaultSection) { mutableStateOf(defaultSection) }
    var settingsPage by rememberSaveable { mutableStateOf("General") }
    val channelIdsWithListings = guide.programmes.map { it.channelId }.toSet()
    val missing = guide.channels.filterNot { channelIdsWithListings.contains(it.id) }
    val newest = guide.programmes.maxByOrNull { it.stop }?.stop

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Lavender.copy(alpha=.13f), SurfaceHigh, SurfaceSoft)))
                    .border(1.dp, Lavender.copy(alpha=.15f), RoundedCornerShape(30.dp)).padding(18.dp)
            ) {
                Box(Modifier.align(Alignment.TopEnd).size(170.dp).background(Brush.radialGradient(listOf(Lavender.copy(alpha=.15f), Color.Transparent))))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Lavender.copy(alpha=.14f)), contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.Settings, null, tint=Lavender, modifier=Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("CONTROL CENTRE", color=Lavender, fontSize=8.sp, fontWeight=FontWeight.Black, letterSpacing=1.sp)
                            Text("Settings", fontSize=23.sp, fontWeight=FontWeight.Black, letterSpacing=(-.55).sp)
                        }
                        CompactPill("v${BuildConfig.VERSION_NAME}", Pink)
                    }
                    Text("Guide behaviour, reminders, updates, channels and what opens first — all in one place.", color=TextSecondary, fontSize=10.sp, lineHeight=15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.035f)).border(1.dp, Hairline, RoundedCornerShape(16.dp)).padding(10.dp)) {
                            Column { Text(if(autoRefresh) "ON" else "OFF", fontWeight=FontWeight.Black, fontSize=15.sp); Text("AUTO REFRESH", color=Cyan, fontSize=7.sp, fontWeight=FontWeight.Black, letterSpacing=.6.sp) }
                        }
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.035f)).border(1.dp, Hairline, RoundedCornerShape(16.dp)).padding(10.dp)) {
                            Column { Text(if(use24Hour) "24H" else "12H", fontWeight=FontWeight.Black, fontSize=15.sp); Text("CLOCK", color=PinkSoft, fontSize=7.sp, fontWeight=FontWeight.Black, letterSpacing=.6.sp) }
                        }
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.035f)).border(1.dp, Hairline, RoundedCornerShape(16.dp)).padding(10.dp)) {
                            Column { Text(channelConfig.size.toString(), fontWeight=FontWeight.Black, fontSize=15.sp); Text("CHANNELS", color=Mint, fontSize=7.sp, fontWeight=FontWeight.Black, letterSpacing=.6.sp) }
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumChoiceChip("General", settingsPage == "General", Pink, Icons.Rounded.Settings) { settingsPage = "General" }
                PremiumChoiceChip("Updates", settingsPage == "Updates", Mint, Icons.Rounded.Refresh) { settingsPage = "Updates" }
                PremiumChoiceChip("Diagnostics", settingsPage == "Diagnostics", Cyan, Icons.Rounded.Info) { settingsPage = "Diagnostics" }
            }
        }

        if (settingsPage == "General") {
        item {
            SettingsCard("Reminders", Icons.Rounded.Notifications, Pink) {
                Text("Default for new favourites", color=TextSecondary, fontSize=10.sp)
                Spacer(Modifier.height(9.dp))
                ReminderMode.entries.forEach { mode ->
                    val selected = reminderMode == mode
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if(selected) Pink.copy(alpha=.10f) else Color.Transparent)
                            .clickable { onReminderMode(mode) }.padding(horizontal=11.dp, vertical=10.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if(selected) Pink else TextSecondary, CircleShape),
                            contentAlignment=Alignment.Center
                        ) { if(selected) Box(Modifier.size(10.dp).background(Pink,CircleShape)) }
                        Spacer(Modifier.width(11.dp)); Text(mode.label, modifier=Modifier.weight(1f), fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium, fontSize=12.sp)
                    }
                }
            }
        }

        item {
            SettingsCard("Guide behaviour", Icons.Rounded.Tv, Cyan) {
                SettingSwitch("24-hour clock", "Use 18:30 instead of 6:30 PM", use24Hour, onUse24Hour)
                HorizontalDivider(color=Hairline, modifier=Modifier.padding(vertical=6.dp))
                SettingSwitch("Background refresh", "Refresh the cached EPG about every six hours", autoRefresh, onAutoRefresh)
            }
        }

        item {
            SettingsCard("Start screen", Icons.Rounded.Home, Lavender) {
                Text("Choose what opens first", color=TextSecondary, fontSize=10.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    AppSection.entries.forEach { item ->
                        PremiumChoiceChip(item.navLabel(), default==item, if(item==AppSection.AI) Lavender else Pink, sectionIcon(item)) {
                            default=item; onDefaultSection(item)
                        }
                    }
                }
            }
        }

        }

        if (settingsPage == "Updates") {
        item {
            SettingsCard("App updates", Icons.Rounded.Refresh, Mint) {
                Text("Installed v${BuildConfig.VERSION_NAME}", fontWeight=FontWeight.Black, fontSize=15.sp)
                Spacer(Modifier.height(4.dp))
                Text("New builds are pre-downloaded in the background. If an update has been published for 24 hours, the app requires that version before continuing.", color=TextSecondary, fontSize=10.sp, lineHeight=14.sp)
                Spacer(Modifier.height(12.dp))
                ManualUpdateControl()
            }
        }

        item {
            SettingsCard("Channel configuration", Icons.Rounded.Refresh, Pink) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${channelConfig.size} configured",fontWeight=FontWeight.Black,fontSize=15.sp); Text("channels.json controls order and groups",color=TextSecondary,fontSize=10.sp) }
                    CompactPill("LIVE", Mint)
                }
                lastUpdated?.let { Spacer(Modifier.height(8.dp)); Text("Last refreshed ${formatTime(it,use24HourForLabel)}",color=TextSecondary,fontSize=10.sp) }
            }
        }

        }

        if (settingsPage == "Diagnostics") {
        item {
            SettingsCard("EPG health", Icons.Rounded.Schedule, if(missing.isEmpty()) Mint else Amber) {
                Row(verticalAlignment=Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("${guide.channels.size - missing.size}/${guide.channels.size}",fontSize=25.sp,fontWeight=FontWeight.Black,letterSpacing=(-.6).sp)
                        Text("channels with listings",color=TextSecondary,fontSize=10.sp)
                    }
                    CompactPill(if(missing.isEmpty()) "HEALTHY" else "CHECK", if(missing.isEmpty()) Mint else Amber)
                }
                Spacer(Modifier.height(10.dp))
                Text("${guide.programmes.size} programmes cached", color=TextSecondary, fontSize=10.sp)
                newest?.let { Text("Guide reaches ${it.format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm",Locale.UK))}",color=TextSecondary,fontSize=10.sp) }
                if(newest!=null && newest.isBefore(ZonedDateTime.now().plusHours(6))) Text("Guide data may be running out soon.",color=Amber,fontSize=10.sp,fontWeight=FontWeight.Bold)
                if(missing.isNotEmpty()) { Spacer(Modifier.height(7.dp)); Text("Missing: ${missing.joinToString { it.name }}",color=PinkSoft,fontSize=10.sp) }
            }
        }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Settings,
    accent: Color = Pink,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(SurfaceHigh,SurfaceSoft)))
            .border(1.dp,Hairline,RoundedCornerShape(26.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha=.13f)),contentAlignment=Alignment.Center) { Icon(icon,null,tint=accent,modifier=Modifier.size(19.dp)) }
            Spacer(Modifier.width(11.dp)); Text(title,fontWeight=FontWeight.Black,fontSize=16.sp,letterSpacing=(-.2).sp)
        }
        Spacer(Modifier.height(13.dp)); content()
    }
}

@Composable
private fun SettingSwitch(label: String, subtitle: String, value: Boolean, onValue: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label,fontWeight=FontWeight.Bold,fontSize=12.sp); Text(subtitle,color=TextSecondary,fontSize=9.sp,lineHeight=13.sp) }
        Switch(
            checked=value,onCheckedChange=onValue,
            colors=SwitchDefaults.colors(checkedThumbColor=TextPrimary,checkedTrackColor=Pink,uncheckedThumbColor=TextSecondary,uncheckedTrackColor=Color.White.copy(alpha=.08f))
        )
    }
}

@Composable
private fun ChannelScheduleSheet(
    channel: TvChannel,
    programmes: List<Programme>,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onProgramme: (Programme) -> Unit,
    use24Hour: Boolean,
    onClose: () -> Unit,
) {
    val upcoming = programmes.filter { it.channelId == channel.id && it.stop.isAfter(ZonedDateTime.now()) }.sortedBy { it.start }.take(30)
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=28.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(SurfaceHigh,SurfaceSoft)))
                .border(1.dp,Hairline,RoundedCornerShape(26.dp)).padding(14.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(17.dp)).background(Panel2.copy(alpha=.88f)).border(1.dp, Hairline, RoundedCornerShape(17.dp)),contentAlignment=Alignment.Center) {
                if(!channel.icon.isNullOrBlank()) AsyncImage(channel.icon,channel.name,Modifier.fillMaxSize().padding(6.dp),contentScale=ContentScale.Fit) else LogoFallback(channel.name)
            }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                Text("CHANNEL",color=Cyan,fontSize=8.sp,fontWeight=FontWeight.Black,letterSpacing=.8.sp)
                Text(channel.name,fontSize=21.sp,fontWeight=FontWeight.Black)
                Text("${upcoming.size} upcoming programmes",color=TextSecondary,fontSize=9.sp)
            }
            PremiumIconButton(Icons.Rounded.Close,"Close",onClick=onClose)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick=onToggleFavourite,
            modifier=Modifier.fillMaxWidth().height(46.dp),
            shape=RoundedCornerShape(16.dp),
            colors=ButtonDefaults.buttonColors(containerColor=if(isFavourite) Pink.copy(alpha=.16f) else Color.White.copy(alpha=.05f), contentColor=if(isFavourite) PinkSoft else TextPrimary)
        ) {
            Icon(if(isFavourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if(isFavourite) "Pinned channel" else "Pin channel",fontWeight=FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp)); PremiumSectionHeader(eyebrow="UP NEXT",title="Channel schedule")
        Spacer(Modifier.height(9.dp))
        LazyColumn(Modifier.heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(upcoming) { p -> ProgrammeListRow(p,channel,false,use24Hour,onProgramme) }
        }
    }
}

@Composable
private fun ProgrammeSheetV2(
    programme: Programme,
    channel: TvChannel?,
    allProgrammes: List<Programme>,
    isFavourite: Boolean,
    isKidsChannel: Boolean,
    reminderMode: ReminderMode,
    use24Hour: Boolean,
    onToggleFavourite: () -> Unit,
    onReminderMode: (ReminderMode) -> Unit,
    onProgramme: (Programme) -> Unit,
    onClose: () -> Unit,
) {
    val context=LocalContext.current
    val now=ZonedDateTime.now()
    val live=!now.isBefore(programme.start)&&now.isBefore(programme.stop)
    val total=Duration.between(programme.start,programme.stop).toMinutes().coerceAtLeast(1)
    val elapsed=Duration.between(programme.start,now).toMinutes().coerceIn(0,total)
    val nextAirings=allProgrammes.filter { sameShowTitle(it.title,programme.title) && it.start.isAfter(programme.start) }.sortedBy { it.start }.take(5)
    val ageGuidance=programmeAgeGuidance(programme,isKidsChannel)

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding=PaddingValues(bottom=30.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(232.dp).background(SurfaceSoft)) {
                val hasArtwork = !programme.icon.isNullOrBlank()
                val image = programme.icon ?: channel?.icon
                if(!image.isNullOrBlank()) {
                    if (hasArtwork) {
                        AsyncImage(image, programme.title, Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Panel3, Panel))))
                        AsyncImage(image, channel?.name ?: programme.channelId, Modifier.align(Alignment.Center).size(120.dp).padding(12.dp), contentScale=ContentScale.Fit)
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Panel3,Panel))),contentAlignment=Alignment.Center){ LogoFallback(channel?.name ?: programme.channelId) }
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x18000000),Color.Transparent,Color(0xEC050912)))))
                Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(live) Surface(color=Pink,shape=RoundedCornerShape(100.dp)) {
                        Row(Modifier.padding(horizontal=9.dp,vertical=5.dp), verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).background(Color(0xFF25000F), CircleShape)); Spacer(Modifier.width(5.dp))
                            Text("LIVE",color=Color(0xFF25000F),fontWeight=FontWeight.Black,fontSize=8.sp,letterSpacing=.8.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f)); PremiumIconButton(Icons.Rounded.Close,"Close",onClick=onClose)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(17.dp)) {
                    Text(channel?.name ?: programme.channelId,color=PinkSoft,fontSize=9.sp,fontWeight=FontWeight.Black,letterSpacing=.5.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(programme.title,color=TextPrimary,fontSize=25.sp,lineHeight=28.sp,fontWeight=FontWeight.Black,letterSpacing=(-.7).sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Surface(color=Color.White.copy(alpha=.045f),shape=RoundedCornerShape(14.dp)) {
                        Text(
                            "${formatTime(programme.start.toLocalTime(),use24Hour)} – ${formatTime(programme.stop.toLocalTime(),use24Hour)}",
                            color=TextPrimary,fontWeight=FontWeight.Bold,fontSize=10.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp)
                        )
                    }
                    Spacer(Modifier.width(7.dp)); AgeGuidanceBadge(ageGuidance)
                    if(programme.isNew){ Spacer(Modifier.width(7.dp)); CompactPill("NEW",Mint) }
                }
                programme.subtitle?.takeIf{it.isNotBlank()}?.let{ Text(it,color=Lavender,fontWeight=FontWeight.Bold,fontSize=12.sp) }
                Text(ageGuidance.explanation,color=TextSecondary,fontSize=10.sp,lineHeight=15.sp)
                if(live){
                    LinearProgressIndicator(progress={elapsed.toFloat()/total.toFloat()},modifier=Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),color=Pink,trackColor=Color.White.copy(alpha=.07f))
                    Text("${(total-elapsed).coerceAtLeast(0)} min left",color=TextSecondary,fontSize=9.sp)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick=onToggleFavourite,
                    modifier=Modifier.weight(1f).height(46.dp),shape=RoundedCornerShape(16.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=if(isFavourite) Pink else Color.White.copy(alpha=.06f),contentColor=if(isFavourite) Color(0xFF21000F) else TextPrimary)
                ) { Icon(if(isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null,Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(if(isFavourite) "Saved" else "Favourite",fontWeight=FontWeight.Black) }
                OutlinedButton(
                    onClick={
                        val share=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,"${programme.title} — ${channel?.name ?: programme.channelId}, ${programme.start.format(DateTimeFormatter.ofPattern("EEEE d MMM 'at' HH:mm",Locale.UK))}") }
                        context.startActivity(Intent.createChooser(share,"Share programme"))
                    },
                    modifier=Modifier.weight(1f).height(46.dp),shape=RoundedCornerShape(16.dp)
                ) { Text("Share",fontWeight=FontWeight.Bold) }
            }
        }
        if(isFavourite) {
            item {
                Column(Modifier.padding(horizontal=16.dp)) {
                    Text("REMINDER",color=PinkSoft,fontSize=9.sp,fontWeight=FontWeight.Black,letterSpacing=.8.sp)
                    Spacer(Modifier.height(8.dp)); Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        ReminderMode.entries.forEach { mode -> PremiumChoiceChip(mode.label,reminderMode==mode,Pink){ onReminderMode(mode) } }
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal=16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel2.copy(alpha=.72f)).border(1.dp,Hairline,RoundedCornerShape(24.dp)).padding(16.dp)) {
                Text(programme.description?.takeIf{it.isNotBlank()} ?: "No description available for this programme.",color=TextSecondary,fontSize=12.sp,lineHeight=19.sp)
            }
        }
        if(nextAirings.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal=16.dp)){ PremiumSectionHeader(eyebrow="NEXT AIRINGS",title="When is this on again?") } }
            items(nextAirings){ p -> Box(Modifier.padding(horizontal=16.dp)){ ProgrammeListRow(p,channel,isFavourite,use24Hour,onProgramme) } }
        }
    }
}

private fun mergedChannels(channels: List<TvChannel>, config: List<ChannelConfig>): List<TvChannel> {
    val configById = config.associateBy { channelKey(it.id) }
    return channels.map { ch ->
        val c = configById[channelKey(ch.id)]
        ch.copy(
            name = c?.name?.takeIf { it.isNotBlank() } ?: ch.name,
            icon = resolvedChannelIcon(ch, c),
            group = c?.group?.takeIf { it.isNotBlank() } ?: ch.group,
        )
    }.filterNot { configById[channelKey(it.id)]?.hidden == true }
        .sortedWith(
            compareBy<TvChannel> { configById[channelKey(it.id)]?.order ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase(Locale.UK) }
        )
}

private fun formatTime(time: LocalTime, use24Hour: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.UK))

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Midnight, Canvas))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Pink.copy(alpha=.18f), Lavender.copy(alpha=.12f)))).border(1.dp, Hairline, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.umay_logo), "Umay TV", Modifier.size(42.dp), contentScale = ContentScale.Fit)
            }
            CircularProgressIndicator(color = Pink, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
            Text("Refreshing your guide", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text("Live listings, favourites and schedules are being prepared.", color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ErrorView(message: String, retry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Midnight, Canvas))).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceSoft)))
                .border(1.dp, StrongHairline, RoundedCornerShape(30.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(20.dp))
                    .background(Pink.copy(alpha = .12f))
                    .border(1.dp, Pink.copy(alpha = .16f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.WifiOff, null, tint = PinkSoft, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Guide unavailable", fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = (-.5).sp)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = retry,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try again", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun GuideContent(
    guide: GuideData,
    channelConfig: List<ChannelConfig>,
    selectedDay: LocalDate,
    onSelectedDay: (LocalDate) -> Unit,
    filter: GuideFilter,
    onFilter: (GuideFilter) -> Unit,
    selectedProgramme: Programme?,
    favouriteShows: Set<String>,
    onProgramme: (Programme) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DayPicker(selectedDay, onSelectedDay)
        FilterPicker(filter, onFilter)

        val channels = remember(
            guide,
            channelConfig,
            filter,
            selectedDay,
            favouriteShows
        ) {
            val configById = channelConfig.associateBy { channelKey(it.id) }

            // Every channel present in guide.xml appears automatically.
            // channels.json is only used for group/order/name/icon overrides.
            val availableChannels = guide.channels
                .map { channel ->
                    val config = configById[channelKey(channel.id)]
                    channel.copy(
                        name = config?.name?.takeIf { it.isNotBlank() } ?: channel.name,
                        icon = resolvedChannelIcon(channel, config),
                    )
                }
                .filterNot { channel ->
                    configById[channelKey(channel.id)]?.hidden == true
                }

            val filtered = availableChannels.filter { channel ->
                val group = configById[channelKey(channel.id)]?.group.orEmpty()

                when (filter) {
                    GuideFilter.ALL -> true
                    GuideFilter.KIDS -> group.equals("Kids", ignoreCase = true)
                    GuideFilter.TURKISH -> group.equals("Turkish TV", ignoreCase = true)
                    GuideFilter.FAVOURITES -> guide.programmes.any { programme ->
                        programme.channelId == channel.id &&
                            programme.start.toLocalDate() == selectedDay &&
                            favouriteShows.any { sameShowTitle(it, programme.title) }
                    }
                }
            }

            filtered.sortedWith(
                compareBy<TvChannel> {
                    configById[channelKey(it.id)]?.order ?: Int.MAX_VALUE
                }.thenBy { it.name.lowercase(Locale.UK) }
            )
        }

        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (filter == GuideFilter.FAVOURITES)
                        "No favourite shows scheduled for this day"
                    else
                        "No channels in this section",
                    color = TextSecondary
                )
            }
        } else {
            TvGrid(
                channels = channels,
                programmes = guide.programmes,
                selectedDay = selectedDay,
                favouriteShows = favouriteShows,
                jumpTarget = GuideJumpTarget.NOW,
                onProgramme = onProgramme,
            )
        }
    }
}

@Composable
private fun DayPicker(selectedDay: LocalDate, onSelectedDay: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val days = (0..5).map { today.plusDays(it.toLong()) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        days.forEach { date ->
            PremiumChoiceChip(
                text = if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d", Locale.UK)),
                selected = date == selectedDay,
                accent = Pink,
                leading = if (date == selectedDay) Icons.Rounded.CalendarMonth else null,
            ) { onSelectedDay(date) }
        }
    }
}

@Composable
private fun FilterPicker(filter: GuideFilter, onFilter: (GuideFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=14.dp, vertical=5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        GuideFilter.entries.forEach { item ->
            PremiumChoiceChip(item.label, item == filter, if (item == GuideFilter.KIDS) Pink else if (item == GuideFilter.TURKISH) Lavender else Cyan) { onFilter(item) }
        }
    }
    Spacer(Modifier.height(4.dp))
}

private val ChannelWidth = 122.dp
private val HalfHourWidth = 124.dp
private const val WindowHours = 6L

@Composable
private fun TvGrid(
    channels: List<TvChannel>,
    programmes: List<Programme>,
    selectedDay: LocalDate,
    favouriteShows: Set<String>,
    jumpTarget: GuideJumpTarget,
    onProgramme: (Programme) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    val guideStart = remember(selectedDay, now.hour) {
        if (selectedDay == now.toLocalDate()) {
            now.withMinute(if (now.minute < 30) 0 else 30)
                .withSecond(0).withNano(0)
                .minusMinutes(30)
        } else {
            selectedDay.atTime(6, 0).atZone(zone)
        }
    }
    val scroll = rememberScrollState()
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val windowHours = if (landscape) 10L else WindowHours
    val guideEnd = guideStart.plusHours(windowHours)
    val effectiveHalfHourWidth = if (landscape) 92.dp else HalfHourWidth
    val pixelsPerMinute = effectiveHalfHourWidth.value / 30f

LaunchedEffect(selectedDay, guideStart, jumpTarget) {
        val target = when (jumpTarget) {
            GuideJumpTarget.NOW -> if (selectedDay == now.toLocalDate()) now else guideStart
            GuideJumpTarget.TONIGHT -> selectedDay.atTime(18, 0).atZone(zone)
            GuideJumpTarget.TOMORROW -> selectedDay.atStartOfDay(zone)
        }

        val minutesFromStart = Duration.between(guideStart, target)
            .toMinutes()
            .coerceIn(0, windowHours * 60)

        val targetPosition = minutesFromStart * pixelsPerMinute
        val visibleGuideWidth = configuration.screenWidthDp - ChannelWidth.value
        val centreOffset = visibleGuideWidth / 2f

        scroll.scrollTo(
            (targetPosition - centreOffset)
                .toInt()
                .coerceAtLeast(0)
        )
    }
    val totalWidth = effectiveHalfHourWidth * (windowHours.toInt() * 2)

    Column(Modifier.fillMaxSize()) {
        TimelineHeader(guideStart, totalWidth, effectiveHalfHourWidth, scroll, windowHours)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 22.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                val channelProgrammes = programmes
                    .asSequence()
                    .filter { it.channelId == channel.id }
                    .filter { it.stop.isAfter(guideStart) && it.start.isBefore(guideEnd) }
                    .sortedBy { it.start }
                    .toList()

                GuideRow(
                    channel = channel,
                    programmes = channelProgrammes,
                    guideStart = guideStart,
                    guideEnd = guideEnd,
                    totalWidth = totalWidth,
                    pixelsPerMinute = pixelsPerMinute,
                    scroll = scroll,
                    now = now,
                    favouriteShows = favouriteShows,
                    onProgramme = onProgramme,
                )
                HorizontalDivider(
                    color = Color(0xFF1C2232),
                    modifier = Modifier.padding(start = ChannelWidth)
                )
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    guideStart: ZonedDateTime,
    totalWidth: Dp,
    halfHourWidth: Dp,
    scroll: androidx.compose.foundation.ScrollState,
    windowHours: Long,
) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).background(SurfaceSoft).border(width = 1.dp, color = Hairline)
    ) {
        Box(Modifier.width(ChannelWidth).fillMaxHeight().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
            Text("Channels", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Row(Modifier.width(totalWidth).fillMaxHeight()) {
                repeat((windowHours * 2).toInt()) { index ->
                    val time = guideStart.plusMinutes(index * 30L)
                    Box(Modifier.width(halfHourWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), color = TextSecondary, fontSize = 10.sp,
                            fontWeight = if (index % 2 == 0) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: TvChannel,
    programmes: List<Programme>,
    guideStart: ZonedDateTime,
    guideEnd: ZonedDateTime,
    totalWidth: Dp,
    pixelsPerMinute: Float,
    scroll: androidx.compose.foundation.ScrollState,
    now: ZonedDateTime,
    favouriteShows: Set<String>,
    onProgramme: (Programme) -> Unit,
) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    Row(
        modifier = Modifier.fillMaxWidth().height(if (landscape) 74.dp else 86.dp).background(Canvas)
    ) {
        ChannelCell(channel)
        Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                if (programmes.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text("No listings yet", color = TextTertiary, fontSize = 10.sp)
                    }
                }
                programmes.forEach { p ->
                    val visibleStart = if (p.start.isBefore(guideStart)) guideStart else p.start
                    val visibleStop = if (p.stop.isAfter(guideEnd)) guideEnd else p.stop
                    val offsetMinutes = Duration.between(guideStart, visibleStart).toMinutes().coerceAtLeast(0)
                    val durationMinutes = Duration.between(visibleStart, visibleStop).toMinutes().coerceAtLeast(5)
                    val x = (offsetMinutes * pixelsPerMinute).dp
                    val width = max(20f, durationMinutes * pixelsPerMinute).dp
                    ProgrammeCard(
                        programme = p,
                        isFavourite = favouriteShows.any { sameShowTitle(it, p.title) },
                        isKidsChannel = channel.group.equals("Kids", ignoreCase = true),
                        modifier = Modifier.offset(x = x, y = 7.dp).width(width - 3.dp).height(if (landscape) 60.dp else 72.dp),
                        onClick = { onProgramme(p) }
                    )
                }
                if (now.toLocalDate() == guideStart.toLocalDate() && !now.isBefore(guideStart) && now.isBefore(guideEnd)) {
                    val nowMinutes = Duration.between(guideStart, now).toMinutes()
                    val x = (nowMinutes * pixelsPerMinute).dp
                    Box(Modifier.offset(x = x).width(2.dp).fillMaxHeight().background(PinkSoft))
                    Box(Modifier.offset(x = x - 4.dp, y = 2.dp).size(10.dp).background(PinkSoft, CircleShape).border(2.dp, Canvas, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun ChannelCell(channel: TvChannel) {
    Row(
        modifier = Modifier.width(ChannelWidth).fillMaxHeight().background(SurfaceSoft.copy(alpha=.88f)).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Panel2.copy(alpha = .92f))
                .border(1.dp, Hairline, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.icon.isNullOrBlank()) {
                SubcomposeAsyncImage(model = channel.icon, contentDescription = channel.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(5.dp)) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                        is AsyncImagePainter.State.Loading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Pink)
                        else -> LogoFallback(channel.name)
                    }
                }
            } else LogoFallback(channel.name)
        }
        Spacer(Modifier.width(8.dp))
        Text(channel.name, color = TextPrimary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LogoFallback(channelName: String) {

    val initials = when (channelName) {
        "BabyFirst" -> "BF"
        "CBeebies" -> "CB"
        "PBS KIDS" -> "PBS"
        "Moonbug Kids" -> "MK"
        "Baby Shark TV" -> "BS"
        "Duck TV" -> "DUCK"
        "ducktv" -> "DUCK"
        "Super Simple Songs" -> "SS"
        "TRT Çocuk" -> "TRT"
        "Minika Çocuk" -> "M"
        "Star TV" -> "★"
        "NOW" -> "NOW"
        "ATV" -> "atv"
        "Show TV" -> "SHOW"
        else -> channelName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF252B43),
                        Color(0xFF161B2C)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = PinkSoft,
            fontWeight = FontWeight.Black,
            fontSize = when {
                initials.length >= 4 -> 9.sp
                initials.length == 3 -> 11.sp
                else -> 14.sp
            }
        )
    }
}



private fun programmeMeta(programme: Programme): String {
    val parts = mutableListOf<String>()
    programme.episodeNumber?.takeIf { it.isNotBlank() }?.let { parts += it }
    programme.originalDate?.takeIf { it.isNotBlank() }?.let { parts += it }
    programme.rating?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (programme.isNew) parts += "NEW"
    return parts.distinct().joinToString(" • ")
}

private fun searchNormalise(value: String): String {
    val lowered = value
        .lowercase(Locale.forLanguageTag("tr-TR"))
        .replace('ı', 'i')
    return Normalizer.normalize(lowered, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()
}

private fun normaliseShowTitle(title: String): String =
    searchNormalise(title)

private fun sameShowTitle(a: String, b: String): Boolean =
    normaliseShowTitle(a) == normaliseShowTitle(b)

private fun loadFavouriteShows(context: Context): Set<String> =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(PREF_FAVOURITES, emptySet())
        ?.toSet()
        .orEmpty()

private fun saveFavouriteShows(context: Context, favourites: Set<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(PREF_FAVOURITES, favourites)
        .apply()
}

private fun loadFavouriteChannels(context: Context): Set<String> =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(PREF_FAVOURITE_CHANNELS, emptySet())
        ?.toSet()
        .orEmpty()

private fun saveFavouriteChannels(context: Context, favourites: Set<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(PREF_FAVOURITE_CHANNELS, favourites)
        .apply()
}

private fun loadReminderMode(context: Context): ReminderMode =
    runCatching {
        ReminderMode.valueOf(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_REMINDER_MODE, ReminderMode.BOTH.name)!!
        )
    }.getOrDefault(ReminderMode.BOTH)

private fun saveReminderMode(context: Context, mode: ReminderMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_REMINDER_MODE, mode.name)
        .apply()
}

private fun loadShowReminderModes(context: Context): Map<String, ReminderMode> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_SHOW_REMINDER_MODES, "{}") ?: "{}"
    return try {
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key ->
                val value = json.optString(key)
                ReminderMode.entries.firstOrNull { it.name == value }?.let { put(key, it) }
            }
        }
    } catch (_: Throwable) {
        emptyMap()
    }
}

private fun saveShowReminderModes(context: Context, modes: Map<String, ReminderMode>) {
    val json = JSONObject()
    modes.forEach { (title, mode) -> json.put(title, mode.name) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_SHOW_REMINDER_MODES, json.toString())
        .apply()
}

private fun reminderModeForTitle(context: Context, title: String): ReminderMode {
    return loadShowReminderModes(context)[normaliseShowTitle(title)] ?: loadReminderMode(context)
}

object ProgrammeReminderScheduler {
    private const val TEN_MINUTES_MS = 10 * 60 * 1000L

    fun scheduleAll(
        context: Context,
        favouriteTitles: Set<String>,
        programmes: List<Programme>,
        channels: List<TvChannel>,
    ) {
        favouriteTitles.forEach { title ->
            scheduleForTitle(context, title, programmes, channels)
        }
    }

    fun scheduleForTitle(
        context: Context,
        title: String,
        programmes: List<Programme>,
        channels: List<TvChannel>,
        explicitMode: ReminderMode? = null,
    ) {
        val now = System.currentTimeMillis()
        programmes
            .filter { sameShowTitle(it.title, title) }
            .filter { it.start.toInstant().toEpochMilli() > now }
            .forEach { programme ->
                val channelIconUrl = channels
                    .firstOrNull { it.id == programme.channelId }
                    ?.icon

                val mode = explicitMode ?: reminderModeForTitle(context, title)
                if (mode == ReminderMode.BOTH || mode == ReminderMode.TEN_MINUTES) {
                    scheduleAlarm(
                        context = context,
                        programme = programme,
                        triggerAtMillis = programme.start.toInstant().toEpochMilli() - TEN_MINUTES_MS,
                        kind = ProgrammeReminderReceiver.KIND_SOON,
                        channelIconUrl = channelIconUrl,
                    )
                } else {
                    cancelAlarm(context, programme, ProgrammeReminderReceiver.KIND_SOON)
                }
                if (mode == ReminderMode.BOTH || mode == ReminderMode.START) {
                    scheduleAlarm(
                        context = context,
                        programme = programme,
                        triggerAtMillis = programme.start.toInstant().toEpochMilli(),
                        kind = ProgrammeReminderReceiver.KIND_NOW,
                        channelIconUrl = channelIconUrl,
                    )
                } else {
                    cancelAlarm(context, programme, ProgrammeReminderReceiver.KIND_NOW)
                }
            }
    }

    fun cancelForTitle(
        context: Context,
        title: String,
        programmes: List<Programme>,
    ) {
        programmes
            .filter { sameShowTitle(it.title, title) }
            .forEach { programme ->
                cancelAlarm(context, programme, ProgrammeReminderReceiver.KIND_SOON)
                cancelAlarm(context, programme, ProgrammeReminderReceiver.KIND_NOW)
            }
    }

    private fun scheduleAlarm(
        context: Context,
        programme: Programme,
        triggerAtMillis: Long,
        kind: String,
        channelIconUrl: String?,
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = reminderPendingIntent(
            context = context,
            programme = programme,
            kind = kind,
            channelIconUrl = channelIconUrl,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(context: Context, programme: Programme, kind: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            reminderPendingIntent(
                context = context,
                programme = programme,
                kind = kind,
                channelIconUrl = null,
            )
        )
    }

    private fun reminderPendingIntent(
        context: Context,
        programme: Programme,
        kind: String,
        channelIconUrl: String?,
    ): PendingIntent {
        val intent = Intent(context, ProgrammeReminderReceiver::class.java).apply {
            putExtra(ProgrammeReminderReceiver.EXTRA_TITLE, programme.title)
            putExtra(ProgrammeReminderReceiver.EXTRA_CHANNEL, programme.channelId)
            putExtra(ProgrammeReminderReceiver.EXTRA_KIND, kind)
            putExtra(ProgrammeReminderReceiver.EXTRA_CHANNEL_ICON_URL, channelIconUrl)
            putExtra(ProgrammeReminderReceiver.EXTRA_PROGRAMME_ICON_URL, programme.icon)
            putExtra(
                ProgrammeReminderReceiver.EXTRA_START_TIME,
                programme.start.format(DateTimeFormatter.ofPattern("HH:mm"))
            )
            putExtra(
                ProgrammeReminderReceiver.EXTRA_START_EPOCH,
                programme.start.toInstant().toEpochMilli()
            )
        }

        val requestCode = (
            normaliseShowTitle(programme.title) +
                "|" + programme.start.toInstant().toEpochMilli() +
                "|" + kind
            ).hashCode()

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

private fun englishCategory(category: String): String {
    return when (category.trim().lowercase()) {
        "film", "sinema", "movie" -> "Movie"
        "dizi", "series" -> "Series"
        "haber", "haberler", "news" -> "News"
        "çocuk", "cocuk", "kids", "children" -> "Kids"
        "spor", "sports" -> "Sports"
        "eğlence", "eglence", "entertainment" -> "Entertainment"
        "belgesel", "documentary" -> "Documentary"
        "müzik", "muzik", "music" -> "Music"
        "yarışma", "yarisma", "game show" -> "Game Show"
        "magazin" -> "Entertainment"
        "yaşam", "yasam", "lifestyle" -> "Lifestyle"
        "animasyon", "animation" -> "Animation"
        "aile", "family" -> "Family"
        "eğitim", "egitim", "education" -> "Education"
        else -> category.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }
}

private data class AgeGuidance(val label: String, val explanation: String)

private fun programmeAgeGuidance(programme: Programme, isKidsChannel: Boolean): AgeGuidance {
    val description = programme.description?.trim().orEmpty()
    if (description.isBlank()) {
        return if (isKidsChannel) {
            AgeGuidance(
                "ADULT SUPERVISION REQUIRED",
                "No programme description was available, so an age recommendation could not be estimated."
            )
        } else {
            AgeGuidance("ADULT", "No programme description was available.")
        }
    }

    if (!isKidsChannel) {
        return AgeGuidance("ADULT", "This programme is not in the Kids group.")
    }

    val text = listOf(programme.title, description, programme.category.orEmpty())
        .joinToString(" ").lowercase(Locale.UK)

    fun containsAny(vararg terms: String) = terms.any { text.contains(it) }

    // Conservative description-based guidance. More specific developmental cues win first.
    return when {
        containsAny("baby sensory", "sensory", "newborn", "infant", "lullaby", "soothing", "tummy time") ->
            AgeGuidance("0–12m", "Baby-focused sensory, soothing or early-development content.")

        containsAny("first words", "baby", "peekaboo", "nursery rhyme", "nursery rhymes", "sing-along", "sing along",
            "colours", "colors", "shapes", "animal sounds", "music", "songs") ->
            AgeGuidance("6m–2y", "Simple words, music, repetition or early-learning themes.")

        containsAny("toddler", "early learning", "counting", "alphabet", "abc", "numbers", "learning to talk",
            "friendship", "playtime") ->
            AgeGuidance("1–3y", "Early toddler learning, language or social-play themes.")

        containsAny("preschool", "pre-school", "phonics", "problem solving", "problem-solving", "kindergarten") ->
            AgeGuidance("2–5y", "Preschool learning or story content.")

        containsAny("school", "science", "history", "adventure", "competition", "quiz", "mystery") ->
            AgeGuidance("4y+", "Themes appear aimed at older children.")

        else -> AgeGuidance("2–4y", "Kids programme with no clear baby-specific developmental cues in its description.")
    }
}

@Composable
private fun AgeGuidanceBadge(guidance: AgeGuidance, compact: Boolean = false) {
    Surface(
        color = if (guidance.label == "ADULT" || guidance.label.startsWith("ADULT SUPERVISION"))
            Color(0xFF5B3A25).copy(alpha = .75f) else Pink.copy(alpha = .14f),
        shape = RoundedCornerShape(99.dp)
    ) {
        Text(
            guidance.label,
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 9.dp, vertical = if (compact) 2.dp else 4.dp),
            color = if (guidance.label == "ADULT" || guidance.label.startsWith("ADULT SUPERVISION"))
                Color(0xFFFFD7B0) else PinkSoft,
            fontSize = if (compact) 8.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgrammeCard(
    programme: Programme,
    isFavourite: Boolean,
    isKidsChannel: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val now = ZonedDateTime.now()
    val live = !now.isBefore(programme.start) && now.isBefore(programme.stop)
    val cardBrush = if (live) Brush.linearGradient(listOf(Pink.copy(alpha=.26f), Lavender.copy(alpha=.17f), SurfaceHigh))
        else Brush.linearGradient(listOf(Panel3.copy(alpha=.92f), SurfaceHigh))

    BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(cardBrush)
            .border(1.dp, if (live) Pink.copy(alpha = .38f) else StrongHairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        val micro = maxWidth < 42.dp
        val tiny = maxWidth < 68.dp
        val compact = maxWidth < 118.dp
        if (micro) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.width(4.dp).fillMaxHeight(.58f).clip(RoundedCornerShape(99.dp))
                        .background(if (live) PinkSoft else Lavender.copy(alpha=.72f))
                )
            }
        } else Column(
            Modifier.fillMaxSize().padding(
                horizontal = if (tiny) 4.dp else 8.dp,
                vertical = if (tiny) 5.dp else 7.dp
            )
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    programme.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (tiny) 8.sp else if (compact) 9.sp else 11.sp,
                    maxLines = if (tiny) 2 else if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (tiny) 9.sp else 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isFavourite && !tiny) {
                    Spacer(Modifier.width(3.dp))
                    Text("♥", color = PinkSoft, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            if (!tiny) {
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live && !compact) {
                        Surface(color = Pink.copy(alpha=.16f), shape = RoundedCornerShape(100.dp)) {
                            Text("NOW", color = PinkSoft, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal=5.dp, vertical=2.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(programme.start.format(DateTimeFormatter.ofPattern("HH:mm")), color = TextSecondary, fontSize = 8.sp)
                    if (!compact) {
                        Spacer(Modifier.width(4.dp))
                        AgeGuidanceBadge(programmeAgeGuidance(programme, isKidsChannel), compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeSheet(
    programme: Programme,
    channel: TvChannel?,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onClose: () -> Unit,
) {
    val now = ZonedDateTime.now()
    val live = !now.isBefore(programme.start) && now.isBefore(programme.stop)
    val total = Duration.between(programme.start, programme.stop).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(programme.start, now).toMinutes().coerceIn(0, total)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 34.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Panel2)
                    .border(
                        1.dp,
                        Color(0xFF252C40),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!channel?.icon.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = channel?.icon,
                        contentDescription = channel?.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp)
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Success -> {
                                SubcomposeAsyncImageContent()
                            }

                            is AsyncImagePainter.State.Loading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Pink
                                )
                            }

                            else -> {
                                LogoFallback(channel?.name ?: programme.channelId)
                            }
                        }
                    }
                } else {
                    LogoFallback(channel?.name ?: programme.channelId)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(channel?.name ?: programme.channelId, color = PinkSoft, fontWeight = FontWeight.Bold)
                Text(
                    "${programme.start.format(DateTimeFormatter.ofPattern("HH:mm"))} – " +
                            programme.stop.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            programme.title,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary
        )

        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onToggleFavourite,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isFavourite) Pink.copy(alpha = .20f) else Panel2,
                contentColor = if (isFavourite) PinkSoft else TextPrimary,
            )
        ) {
            Icon(
                if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isFavourite) "Favourite show" else "Add to favourite shows")
        }

        if (isFavourite) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Alerts are set for 10 minutes before and when the show starts.",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        programme.category?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            SuggestionChip(
                onClick = {},
                label = { Text(englishCategory(it)) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Pink.copy(alpha = .12f),
                    labelColor = PinkSoft
                ),
                border = null
            )
        }

        if (live) {
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { elapsed.toFloat() / total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Pink,
                trackColor = Color(0xFF2A3042)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${(total - elapsed).coerceAtLeast(0)} min left",
                color = PinkSoft,
                fontSize = 12.sp
            )
        }

        programme.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, color = Color(0xFFD6D7E0), lineHeight = 22.sp)
        } ?: run {
            Spacer(Modifier.height(20.dp))
            Text(
                "No description available for this programme.",
                color = TextSecondary,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(20.dp))
        Surface(
            color = Panel2,
            shape = RoundedCornerShape(26.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = Lavender)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        programme.start.format(
                            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${programme.start.format(DateTimeFormatter.ofPattern("HH:mm"))} – " +
                                programme.stop.format(DateTimeFormatter.ofPattern("HH:mm")),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

object ChannelConfigRepository {
    fun loadCached(context: Context, url: String, cacheName: String): List<ChannelConfig> {
        val cache = File(context.filesDir, cacheName)
        return try {
            val result = load(url)
            cache.writeText(channelConfigToJson(result), Charsets.UTF_8)
            result
        } catch (t: Throwable) {
            if (cache.exists()) parseJson(cache.readText(Charsets.UTF_8)) else DefaultChannelConfig
        }
    }

    private fun channelConfigToJson(items: List<ChannelConfig>): String {
        val array = JSONArray()
        items.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("group", item.group)
            obj.put("order", item.order)
            item.name?.let { obj.put("name", it) }
            item.icon?.let { obj.put("icon", it) }
            obj.put("hidden", item.hidden)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseJson(json: String): List<ChannelConfig> {
        val array = JSONArray(json)
        val result = mutableListOf<ChannelConfig>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.optString("id").trim()
            if (id.isBlank()) continue
            result += ChannelConfig(
                id = id,
                group = item.optString("group", "").trim(),
                order = item.optInt("order", Int.MAX_VALUE),
                name = item.optString("name", "").trim().takeIf { it.isNotBlank() },
                icon = item.optString("icon", "").trim().takeIf { it.isNotBlank() },
                hidden = item.optBoolean("hidden", false),
            )
        }
        return result.ifEmpty { DefaultChannelConfig }
    }

    fun load(url: String): List<ChannelConfig> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "UmayTVGuide/2.0")
        connection.instanceFollowRedirects = true
        try {
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseJson(json)
        } finally {
            connection.disconnect()
        }
    }

}

object XmlTvRepository {
    private const val MIN_PROGRAMMES = 5

    fun loadCached(context: Context, url: String, cacheName: String): GuideData {
        val cache = File(context.filesDir, cacheName)

        if (!isNetworkAvailable(context) && cache.exists()) {
            return XmlTvParser.parse(openMaybeGzip(cache.readBytes(), cacheName))
        }

        return try {
            val bytes = downloadBytes(url)
            val candidate = XmlTvParser.parse(openMaybeGzip(bytes, url))
            validate(candidate)

            val tmp = File(context.filesDir, "$cacheName.tmp")
            tmp.writeBytes(bytes)
            if (cache.exists()) cache.delete()
            tmp.renameTo(cache)
            candidate
        } catch (t: Throwable) {
            if (cache.exists()) {
                XmlTvParser.parse(openMaybeGzip(cache.readBytes(), cacheName))
            } else throw t
        }
    }

    fun load(url: String): GuideData {
        val bytes = downloadBytes(url)
        val guide = XmlTvParser.parse(openMaybeGzip(bytes, url))
        validate(guide)
        return guide
    }

    private fun validate(guide: GuideData) {
        require(guide.channels.isNotEmpty()) { "EPG validation failed: no channels" }
        require(guide.programmes.size >= MIN_PROGRAMMES) { "EPG validation failed: too few programmes" }
        require(guide.programmes.all { it.stop.isAfter(it.start) }) { "EPG validation failed: invalid programme times" }
        val newest = guide.programmes.maxByOrNull { it.stop }?.stop
        require(newest != null && newest.isAfter(ZonedDateTime.now().minusHours(2))) {
            "EPG validation failed: guide appears stale"
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.setRequestProperty("User-Agent", "UmayTVGuide/2.1")
        connection.instanceFollowRedirects = true
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openMaybeGzip(bytes: ByteArray, name: String): java.io.InputStream {
        val raw = BufferedInputStream(bytes.inputStream())
        val gzipMagic = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        return if (name.endsWith(".gz", true) || gzipMagic) GZIPInputStream(raw) else raw
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

object XmlTvParser {
    private val formatterWithZone = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z", Locale.UK)
    private val formatterNoZone = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.UK)

    fun parse(input: java.io.InputStream): GuideData {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        val channels = linkedMapOf<String, TvChannel>()
        val programmes = mutableListOf<Programme>()

        var event = parser.eventType

        var channelId: String? = null
        var channelName: String? = null
        var channelIcon: String? = null

        var pChannel: String? = null
        var pStart: ZonedDateTime? = null
        var pStop: ZonedDateTime? = null
        var pTitle: String? = null
        var pDesc: String? = null
        var pCategory: String? = null
        var pIcon: String? = null
        var pSubtitle: String? = null
        var pEpisodeNumber: String? = null
        var pOriginalDate: String? = null
        var pRating: String? = null
        var pIsNew = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> {
                        channelId = parser.getAttributeValue(null, "id")
                        channelName = null
                        channelIcon = null
                    }
                    "display-name" -> if (channelId != null) {
                        channelName = parser.nextText()
                    }
                    "icon" -> {
                        if (pChannel != null) {
                            pIcon = parser.getAttributeValue(null, "src")
                        } else if (channelId != null) {
                            channelIcon = parser.getAttributeValue(null, "src")
                        }
                    }
                    "programme" -> {
                        pChannel = parser.getAttributeValue(null, "channel")
                        pStart = parseXmlTvTime(parser.getAttributeValue(null, "start"))
                        pStop = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
                        pTitle = null
                        pDesc = null
                        pCategory = null
                        pIcon = null
                        pSubtitle = null
                        pEpisodeNumber = null
                        pOriginalDate = null
                        pRating = null
                        pIsNew = false
                    }
                    "title" -> if (pChannel != null) pTitle = parser.nextText()
                    "sub-title" -> if (pChannel != null) pSubtitle = parser.nextText()
                    "desc" -> if (pChannel != null) pDesc = parser.nextText()
                    "episode-num" -> if (pChannel != null && pEpisodeNumber == null) pEpisodeNumber = parser.nextText()
                    "date" -> if (pChannel != null) pOriginalDate = parser.nextText()
                    "new" -> if (pChannel != null) pIsNew = true
                    "value" -> if (pChannel != null && pRating == null) pRating = parser.nextText()
                    "category" -> if (pChannel != null && pCategory == null) {
                        pCategory = parser.nextText()
                    }
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "channel" -> {
                        val id = channelId
                        if (id != null) {
                            channels[id] = TvChannel(
                                id = id,
                                name = channelName?.takeIf { it.isNotBlank() } ?: id,
                                icon = channelIcon,
                            )
                        }
                        channelId = null
                    }
                    "programme" -> {
                        val ch = pChannel
                        val start = pStart
                        val stop = pStop
                        val title = pTitle
                        val cleanedTitle = title?.trim()
                        val placeholderTitle = cleanedTitle?.lowercase(Locale.ROOT)?.let { normalised ->
                            normalised.contains("programmes start at") ||
                                normalised.contains("programs start at") ||
                                normalised.contains("programming starts at") ||
                                normalised == "no programme information" ||
                                normalised == "no program information"
                        } ?: false

                        if (ch != null && start != null && stop != null &&
                            !cleanedTitle.isNullOrBlank() && !placeholderTitle
                        ) {
                            programmes += Programme(
                                channelId = ch,
                                start = start.withZoneSameInstant(ZoneId.systemDefault()),
                                stop = stop.withZoneSameInstant(ZoneId.systemDefault()),
                                title = cleanedTitle,
                                description = pDesc,
                                category = pCategory,
                                icon = pIcon,
                                subtitle = pSubtitle,
                                episodeNumber = pEpisodeNumber,
                                originalDate = pOriginalDate,
                                rating = pRating,
                                isNew = pIsNew,
                            )
                        }
                        pChannel = null
                    }
                }
            }
            event = parser.next()
        }

        return GuideData(
            channels = channels.values.toList(),
            programmes = programmes,
        )
    }

    private fun parseXmlTvTime(value: String?): ZonedDateTime? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        return try {
            ZonedDateTime.parse(trimmed, formatterWithZone)
        } catch (_: Throwable) {
            try {
                LocalDateTime.parse(trimmed.take(14), formatterNoZone)
                    .atZone(ZoneId.systemDefault())
            } catch (_: Throwable) {
                null
            }
        }
    }
}
