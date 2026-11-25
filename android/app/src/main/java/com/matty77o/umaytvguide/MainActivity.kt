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

private fun adviserErrorText(root: JSONObject): String? {
    if (!root.has("error") || root.isNull("error")) return null
    val value = root.opt("error")
    if (value == false) return null
    val text = (value as? JSONObject)?.optString("message") ?: value.toString()
    return text.trim().takeIf { it.isNotBlank() && it != "false" && it != "null" }
}

private fun adviserFailureReason(error: Throwable): String = when (error) {
    is java.net.SocketTimeoutException -> "Reason: the AI took too long to reply (timed out)."
    is java.net.UnknownHostException -> "Reason: no internet connection, or the AI address couldn't be found."
    is javax.net.ssl.SSLException -> "Reason: secure connection to the AI failed."
    is org.json.JSONException -> "Reason: the AI replied in an unexpected format."
    else -> "Reason: " + (error.message?.take(180) ?: error.javaClass.simpleName)
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
        if (code !in 200..299) error("HTTP $code" + text.trim().take(160).let { if (it.isBlank()) "" else ": $it" })

        val root = JSONObject(text)
        adviserErrorText(root)?.let { if (!root.has("summary") && !root.has("answer") && !root.has("existing")) error(it) }

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