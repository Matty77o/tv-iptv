package com.matty77o.umaytvguide

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
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private const val PREFS_NAME = "umay_tv_guide"
private const val PREF_FAVOURITES = "favourite_show_titles"
private const val PREF_FAVOURITE_CHANNELS = "favourite_channels"
private const val PREF_REMINDER_MODE = "reminder_mode"
private const val PREF_SHOW_REMINDER_MODES = "show_reminder_modes"
private const val PREF_TIME_24 = "time_24_hour"
private const val PREF_AUTO_REFRESH = "auto_refresh"
private const val PREF_DEFAULT_SECTION = "default_section"

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
    ChannelConfig("Super Simple Songs", "Kids", 60),
    ChannelConfig("TRT Çocuk", "Kids", 70),
    ChannelConfig("Minika Çocuk", "Kids", 80),
    ChannelConfig("Star TV", "Turkish TV", 100),
    ChannelConfig("NOW", "Turkish TV", 110),
    ChannelConfig("ATV", "Turkish TV", 120),
    ChannelConfig("Show TV", "Turkish TV", 130),
)

data class TvChannel(
    val id: String,
    val name: String,
    val icon: String?,
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
    HOME("Home"), GUIDE("Guide"), FAVOURITES("Favourites"), SETTINGS("Settings")
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    GuideScreen(
                        reminderOpenRequest = reminderOpenRequest.value,
                        onReminderConsumed = { reminderOpenRequest.value = null },
                    )
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

private val Midnight = Color(0xFF090B15)
private val Panel = Color(0xFF111626)
private val Panel2 = Color(0xFF171D30)
private val Pink = Color(0xFFFF62A8)
private val PinkSoft = Color(0xFFFFA7CF)
private val Lavender = Color(0xFFBFA7FF)
private val Cyan = Color(0xFF70D7FF)
private val Mint = Color(0xFF72E5C2)
private val TextPrimary = Color(0xFFF8F7FC)
private val TextSecondary = Color(0xFFAAAEC0)

@Composable
fun UmayTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Pink,
        secondary = Lavender,
        tertiary = Cyan,
        background = Midnight,
        surface = Panel,
        surfaceVariant = Panel2,
        onPrimary = Color(0xFF240012),
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    reminderOpenRequest: ReminderOpenRequest? = null,
    onReminderConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
                title = {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            placeholder = { Text("Search English or Turkish shows") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { searchQuery = ""; searchOpen = false }) {
                                    Icon(Icons.Rounded.Close, "Close search")
                                }
                            }
                        )
                    } else {
                        Column {
                            Text("Umay TV Guide", fontWeight = FontWeight.Black)
                            lastUpdated?.let {
                                Text(
                                    "Updated ${formatTime(it, use24Hour)}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!searchOpen) {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Rounded.Search, "Search")
                        }
                        IconButton(onClick = { refreshToken++ }) {
                            Icon(Icons.Rounded.Refresh, "Refresh")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                AppSection.entries.forEach { item ->
                    val icon = when (item) {
                        AppSection.HOME -> Icons.Rounded.Home
                        AppSection.GUIDE -> Icons.Rounded.Tv
                        AppSection.FAVOURITES -> Icons.Rounded.Favorite
                        AppSection.SETTINGS -> Icons.Rounded.Settings
                    }
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item; searchOpen = false },
                        icon = { Icon(icon, null) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
                    } else when (section) {
                        AppSection.HOME -> HomeView(
                            guide = currentGuide,
                            channels = visibleChannels,
                            channelConfig = channelConfig,
                            favouriteShows = favouriteShows,
                            favouriteChannels = favouriteChannels,
                            onProgramme = { selectedProgramme = it },
                            onChannel = { selectedChannel = it },
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
                            use24Hour = use24Hour,
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

                    AnimatedVisibility(
                        visible = loading,
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Pink,
                            trackColor = Panel2
                        )
                    }
                }
            }
        }
    }

    selectedProgramme?.let { programme ->
        val currentGuide = guide
        ModalBottomSheet(
            onDismissRequest = { selectedProgramme = null },
            containerColor = Panel,
            contentColor = TextPrimary,
        ) {
            val isFavourite = favouriteShows.any { sameShowTitle(it, programme.title) }
            val effectiveReminderMode = showReminderModes[normaliseShowTitle(programme.title)] ?: reminderMode
            ProgrammeSheetV2(
                programme = programme,
                channel = currentGuide?.channels?.firstOrNull { it.id == programme.channelId },
                allProgrammes = currentGuide?.programmes.orEmpty(),
                isFavourite = isFavourite,
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
            containerColor = Panel,
            contentColor = TextPrimary,
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
private fun HomeView(
    guide: GuideData,
    channels: List<TvChannel>,
    channelConfig: List<ChannelConfig>,
    favouriteShows: Set<String>,
    favouriteChannels: Set<String>,
    onProgramme: (Programme) -> Unit,
    onChannel: (TvChannel) -> Unit,
    onOpenGuide: (String) -> Unit,
    use24Hour: Boolean,
) {
    val now = ZonedDateTime.now()

    // Home is deliberately kept as a quick dashboard. Full channel/category
    // browsing lives in Guide so the same information is not repeated twice.
    // Some XMLTV feeds can contain the same programme more than once.
    // De-duplicate before rendering Home so one broadcast only gets one card.
    val onNow = guide.programmes
        .filter { !now.isBefore(it.start) && now.isBefore(it.stop) }
        .distinctBy {
            Triple(
                it.channelId.trim().lowercase(Locale.ROOT),
                normaliseShowTitle(it.title),
                it.start.toInstant(),
            )
        }
        .sortedBy { programme ->
            channels.indexOfFirst { it.id == programme.channelId }
                .let { if (it < 0) Int.MAX_VALUE else it }
        }

    val startingSoon = guide.programmes
        .filter { it.start.isAfter(now) && !it.start.isAfter(now.plusMinutes(30)) }
        .distinctBy {
            Triple(
                it.channelId.trim().lowercase(Locale.ROOT),
                normaliseShowTitle(it.title),
                it.start.toInstant(),
            )
        }
        .sortedBy { it.start }
        .take(10)

    val favouriteUpcoming = guide.programmes
        .filter { it.start.isAfter(now) && favouriteShows.any { fav -> sameShowTitle(fav, it.title) } }
        .sortedBy { it.start }
        .take(8)

    // Shared household agenda: favourite programmes still to come today.
    // This intentionally stays household-wide rather than introducing profiles.
    val forUsToday = guide.programmes
        .filter {
            it.start.isAfter(now) &&
                it.start.toLocalDate() == now.toLocalDate() &&
                favouriteShows.any { fav -> sameShowTitle(fav, it.title) }
        }
        .distinctBy {
            Triple(
                it.channelId.trim().lowercase(Locale.ROOT),
                normaliseShowTitle(it.title),
                it.start.toInstant(),
            )
        }
        .sortedBy { it.start }
        .take(6)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel2),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        "Umay TV Guide",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Now, next and everything worth remembering.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        if (forUsToday.isNotEmpty()) {
            item {
                SectionHeader("For us today") { onOpenGuide("All") }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel2),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        forUsToday.forEachIndexed { index, programme ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onProgramme(programme) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatTime(programme.start.toLocalTime(), use24Hour),
                                    color = Pink,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(58.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        programme.title,
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        channels.firstOrNull { it.id == programme.channelId }?.name
                                            ?: programme.channelId,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (index != forUsToday.lastIndex) {
                                HorizontalDivider(color = TextSecondary.copy(alpha = 0.16f))
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("On now") { onOpenGuide("All") }
            if (onNow.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel2),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Nothing is live in the guide right now.",
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(onNow) { p ->
                        ProgrammePosterCard(
                            programme = p,
                            channel = channels.firstOrNull { it.id == p.channelId },
                            use24Hour = use24Hour,
                            onClick = { onProgramme(p) }
                        )
                    }
                }
            }
        }

        if (startingSoon.isNotEmpty()) {
            item {
                SectionHeader("Starting soon") { onOpenGuide("All") }
                Card(colors = CardDefaults.cardColors(containerColor = Panel2)) {
                    Column {
                        startingSoon.take(5).forEachIndexed { index, p ->
                            val channel = channels.firstOrNull { it.id == p.channelId }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onProgramme(p) }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatTime(p.start.toLocalTime(), use24Hour),
                                    color = PinkSoft,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(58.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(channel?.name ?: p.channelId, color = TextSecondary, fontSize = 12.sp)
                                }
                                Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
                            }
                            if (index < minOf(4, startingSoon.lastIndex)) {
                                HorizontalDivider(color = TextSecondary.copy(alpha = 0.12f))
                            }
                        }
                    }
                }
            }
        }

        if (favouriteUpcoming.isNotEmpty()) {
            item {
                SectionHeader("Coming up in favourites") { }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favouriteUpcoming) { p ->
                        ProgrammePosterCard(
                            programme = p,
                            channel = channels.firstOrNull { it.id == p.channelId },
                            use24Hour = use24Hour,
                            onClick = { onProgramme(p) }
                        )
                    }
                }
            }
        }

        if (favouriteChannels.isNotEmpty()) {
            item {
                SectionHeader("Pinned channels") { }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(channels.filter { favouriteChannels.contains(it.id) }) { ch ->
                        ChannelTile(ch) { onChannel(ch) }
                    }
                }
            }
        }
    }
}
@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll) {
            Text("See all")
            Icon(Icons.Rounded.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ChannelTile(channel: TvChannel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(112.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel2)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                if (!channel.icon.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = channel.icon,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (painter.state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent()
                        else LogoFallback(channel.name)
                    }
                } else LogoFallback(channel.name)
            }
            Spacer(Modifier.height(6.dp))
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
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
    Card(colors = CardDefaults.cardColors(containerColor = Panel2)) {
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
    Card(
        modifier = Modifier.width(180.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel2)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(95.dp).background(Panel)) {
                val image = programme.icon ?: channel?.icon
                if (!image.isNullOrBlank()) {
                    AsyncImage(image, programme.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LogoFallback(channel?.name ?: programme.channelId) }
            }
            Column(Modifier.padding(10.dp)) {
                val now = ZonedDateTime.now()
                val isLive = !now.isBefore(programme.start) && now.isBefore(programme.stop)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        programme.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isLive) {
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE", color = PinkSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("${channel?.name ?: programme.channelId} • ${formatTime(programme.start.toLocalTime(), use24Hour)}",
                    color = TextSecondary, fontSize = 11.sp)
                if (isLive) {
                    val total = Duration.between(programme.start, programme.stop).toMinutes().coerceAtLeast(1)
                    val elapsed = Duration.between(programme.start, now).toMinutes().coerceIn(0, total)
                    LinearProgressIndicator(
                        progress = { elapsed.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    Text(
                        "${(total - elapsed).coerceAtLeast(0)} min left",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
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
    val configById = channelConfig.associateBy { it.id }
    val groups = listOf("All", "Favourite Shows") + channelConfig
        .map { it.group }.filter { it.isNotBlank() }.distinct()
    val visible = channels.filter { channel ->
        when (selectedGroup) {
            "All" -> true
            "Favourite Shows" -> guide.programmes.any { p ->
                p.channelId == channel.id && p.start.toLocalDate() == selectedDay &&
                    favouriteShows.any { sameShowTitle(it, p.title) }
            }
            else -> configById[channel.id]?.group == selectedGroup
        }
    }

    Column(Modifier.fillMaxSize()) {
        DayPicker(selectedDay, onSelectedDay)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                shape = RoundedCornerShape(22.dp),onClick = { onSelectedDay(LocalDate.now()); jumpTarget = GuideJumpTarget.NOW }, label = { Text("NOW") })
            AssistChip(
                shape = RoundedCornerShape(22.dp),onClick = { onSelectedDay(LocalDate.now()); jumpTarget = GuideJumpTarget.TONIGHT }, label = { Text("Tonight") })
            AssistChip(
                shape = RoundedCornerShape(22.dp),onClick = { onSelectedDay(LocalDate.now().plusDays(1)); jumpTarget = GuideJumpTarget.TOMORROW }, label = { Text("Tomorrow") })
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
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
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                if (query.length < 2) "Type at least 2 characters" else "${results.size} results",
                color = TextSecondary
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
    use24Hour: Boolean,
) {
    val now = ZonedDateTime.now()
    val upcoming = guide.programmes
        .filter { it.stop.isAfter(now) && favouriteShows.any { f -> sameShowTitle(f, it.title) } }
        .sortedBy { it.start }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("My shows", fontSize = 24.sp, fontWeight = FontWeight.Black) }
        if (favouriteChannels.isNotEmpty()) {
            item {
                Text("Pinned channels", fontWeight = FontWeight.Bold, color = PinkSoft)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channels.filter { favouriteChannels.contains(it.id) }) { ch -> ChannelTile(ch) { onChannel(ch) } }
                }
            }
        }
        val weekly = upcoming
            .filter { it.start.toLocalDate() <= LocalDate.now().plusDays(6) }
            .groupBy { it.start.toLocalDate() }
        if (weekly.isNotEmpty()) {
            item {
                Text("This week", fontWeight = FontWeight.Bold, color = PinkSoft)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    weekly.entries.sortedBy { it.key }.forEach { (day, shows) ->
                        Text(
                            day.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK)),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        shows.take(5).forEach { p ->
                            Text(
                                "${formatTime(p.start.toLocalTime(), use24Hour)}  ${p.title}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        if (upcoming.isEmpty()) {
            item { Text("Favourite a programme and its future airings will appear here.", color = TextSecondary) }
        } else {
            items(upcoming.take(100)) { p ->
                ProgrammeListRow(p, channels.firstOrNull { it.id == p.channelId }, true, use24Hour, onProgramme)
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onProgramme(programme) },
        colors = CardDefaults.cardColors(containerColor = Panel2)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val image = programme.icon ?: channel?.icon
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                if (!image.isNullOrBlank()) AsyncImage(image, programme.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else LogoFallback(channel?.name ?: programme.channelId)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        programme.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    if (favourite) Text("♥", color = PinkSoft)
                }
                Text(channel?.name ?: programme.channelId, color = PinkSoft, fontSize = 12.sp)
                val meta = programmeMeta(programme)
                if (meta.isNotBlank()) {
                    Text(meta, color = Lavender, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${programme.start.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))} • ${formatTime(programme.start.toLocalTime(), use24Hour)}",
                    color = TextSecondary, fontSize = 11.sp
                )
            }
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Black) }
        item {
            SettingsCard("Default reminder for new favourites") {
                ReminderMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onReminderMode(mode) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reminderMode == mode, onClick = { onReminderMode(mode) })
                        Text(mode.label)
                    }
                }
            }
        }
        item {
            SettingsCard("Guide") {
                SettingSwitch("24-hour clock", use24Hour, onUse24Hour)
                SettingSwitch("Background guide refresh", autoRefresh, onAutoRefresh)
                Text("When enabled, Android refreshes the cached EPG about every 6 hours.", color = TextSecondary, fontSize = 11.sp)
            }
        }
        item {
            SettingsCard("Start screen") {
                AppSection.entries.forEach { item ->
                    FilterChip(
                        selected = default == item,
                        onClick = { default = item; onDefaultSection(item) },
                        label = { Text(item.label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }
        item {
            SettingsCard("Dynamic channel setup") {
                Text("${channelConfig.size} configured channels", fontWeight = FontWeight.SemiBold)
                Text("Categories and channel order come from channels.json. New groups appear automatically without rebuilding the APK.", color = TextSecondary)
                lastUpdated?.let { Text("Last refreshed ${formatTime(it, use24HourForLabel)}", color = PinkSoft, fontSize = 12.sp) }
            }
        }

        item {
            val channelIdsWithListings = guide.programmes.map { it.channelId }.toSet()
            val missing = guide.channels.filterNot { channelIdsWithListings.contains(it.id) }
            val newest = guide.programmes.maxByOrNull { it.stop }?.stop
            SettingsCard("EPG health") {
                Text("${guide.channels.size} channels • ${guide.programmes.size} programmes", fontWeight = FontWeight.SemiBold)
                Text(
                    "${guide.channels.size - missing.size}/${guide.channels.size} channels currently have listings",
                    color = if (missing.isEmpty()) Mint else PinkSoft
                )
                newest?.let {
                    Text(
                        "Guide reaches ${it.format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.UK))}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                if (newest != null && newest.isBefore(ZonedDateTime.now().plusHours(6))) {
                    Text(
                        "Guide data may be stale or running out soon.",
                        color = PinkSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Missing EPG: " + missing.joinToString { it.name },
                        color = PinkSoft,
                        fontSize = 12.sp
                    )
                }
                Text(
                    "New downloads are validated before replacing the last-known-good cache.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel2)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, color = PinkSoft, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, onValue: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onValue)
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelTile(channel) { }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontSize = 24.sp, fontWeight = FontWeight.Black)
                FilledTonalButton(onClick = onToggleFavourite) {
                    Icon(if (isFavourite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFavourite) "Pinned channel" else "Pin channel")
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Upcoming", fontWeight = FontWeight.Bold, color = PinkSoft)
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(upcoming) { p -> ProgrammeListRow(p, channel, false, use24Hour, onProgramme) }
        }
    }
}

@Composable
private fun ProgrammeSheetV2(
    programme: Programme,
    channel: TvChannel?,
    allProgrammes: List<Programme>,
    isFavourite: Boolean,
    reminderMode: ReminderMode,
    use24Hour: Boolean,
    onToggleFavourite: () -> Unit,
    onReminderMode: (ReminderMode) -> Unit,
    onProgramme: (Programme) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val now = ZonedDateTime.now()
    val live = !now.isBefore(programme.start) && now.isBefore(programme.stop)
    val total = Duration.between(programme.start, programme.stop).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(programme.start, now).toMinutes().coerceIn(0, total)
    val nextAirings = allProgrammes.filter {
        sameShowTitle(it.title, programme.title) && it.start.isAfter(programme.start)
    }.sortedBy { it.start }.take(5)

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp)).background(Panel2)) {
                val image = programme.icon ?: channel?.icon
                if (!image.isNullOrBlank()) {
                    AsyncImage(image, programme.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LogoFallback(channel?.name ?: programme.channelId) }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                }
            }
        }
        item {
            Text(channel?.name ?: programme.channelId, color = PinkSoft, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(programme.title, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                if (programme.isNew) {
                    AssistChip(
                shape = RoundedCornerShape(22.dp),onClick = {}, label = { Text("NEW") })
                }
            }
            programme.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Lavender, fontWeight = FontWeight.SemiBold)
            }
            val meta = programmeMeta(programme)
            if (meta.isNotBlank()) Text(meta, color = TextSecondary, fontSize = 12.sp)
            Text(
                "${programme.start.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK))} • " +
                    "${formatTime(programme.start.toLocalTime(), use24Hour)} – ${formatTime(programme.stop.toLocalTime(), use24Hour)} • " +
                    "${Duration.between(programme.start, programme.stop).toMinutes().coerceAtLeast(1)} min",
                color = TextSecondary
            )
        }
        item {
            FilledTonalButton(onClick = onToggleFavourite) {
                Icon(if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isFavourite) "Favourite show" else "Add to favourite shows")
            }
            if (isFavourite) {
                Spacer(Modifier.height(8.dp))
                Text("Reminder for this show", color = PinkSoft, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReminderMode.entries.forEach { mode ->
                        FilterChip(
                            selected = reminderMode == mode,
                            onClick = { onReminderMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${programme.title} — ${channel?.name ?: programme.channelId}, " +
                                programme.start.format(DateTimeFormatter.ofPattern("EEEE d MMM 'at' HH:mm", Locale.UK))
                        )
                    }
                    context.startActivity(Intent.createChooser(share, "Share programme"))
                }
            ) {
                Text("Share programme")
            }
        }
        programme.category?.takeIf { it.isNotBlank() }?.let { category ->
            item { SuggestionChip(onClick = {}, label = { Text(englishCategory(category)) }) }
        }
        if (live) {
            item {
                LinearProgressIndicator(
                    progress = { elapsed.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                    color = Pink, trackColor = Color(0xFF2A3042)
                )
                Text("${(total - elapsed).coerceAtLeast(0)} min left", color = PinkSoft, fontSize = 12.sp)
            }
        }
        item {
            Text(programme.description?.takeIf { it.isNotBlank() } ?: "No description available for this programme.",
                color = Color(0xFFD6D7E0), lineHeight = 22.sp)
        }
        if (nextAirings.isNotEmpty()) {
            item { Text("When is this next on?", color = PinkSoft, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            items(nextAirings) { p -> ProgrammeListRow(p, channel, isFavourite, use24Hour, onProgramme) }
        }
    }
}

private fun mergedChannels(channels: List<TvChannel>, config: List<ChannelConfig>): List<TvChannel> {
    val configById = config.associateBy { it.id }
    return channels.map { ch ->
        val c = configById[ch.id]
        ch.copy(
            name = c?.name?.takeIf { it.isNotBlank() } ?: ch.name,
            icon = c?.icon?.takeIf { it.isNotBlank() } ?: ch.icon,
        )
    }.filterNot { configById[it.id]?.hidden == true }
        .sortedWith(compareBy<TvChannel> { configById[it.id]?.order ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.UK) })
}

private fun formatTime(time: LocalTime, use24Hour: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.UK))

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(28.dp),
                color = Panel2,
                tonalElevation = 6.dp
            ) {
                AsyncImage(
                    model = "android.resource://com.matty77o.umaytvguide/drawable/umay_logo",
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(22.dp))
            Text("Loading Umay's guide…", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(190.dp).clip(RoundedCornerShape(99.dp)),
                color = Pink,
                trackColor = Panel2
            )
        }
    }
}

@Composable
private fun ErrorView(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel2)) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Couldn't load the guide", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(message, color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Button(onClick = retry) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Try again")
                }
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
            val configById = channelConfig.associateBy { it.id }

            // Every channel present in guide.xml appears automatically.
            // channels.json is only used for group/order/name/icon overrides.
            val availableChannels = guide.channels
                .map { channel ->
                    val config = configById[channel.id]
                    channel.copy(
                        name = config?.name?.takeIf { it.isNotBlank() } ?: channel.name,
                        icon = config?.icon?.takeIf { it.isNotBlank() } ?: channel.icon,
                    )
                }
                .filterNot { channel ->
                    configById[channel.id]?.hidden == true
                }

            val filtered = availableChannels.filter { channel ->
                val group = configById[channel.id]?.group.orEmpty()

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
                    configById[it.id]?.order ?: Int.MAX_VALUE
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
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEach { date ->
            val selected = date == selectedDay
            FilterChip(
                selected = selected,
                onClick = { onSelectedDay(date) },
                label = {
                    Text(
                        if (date == today) "Today"
                        else date.format(DateTimeFormatter.ofPattern("EEE d", Locale.UK))
                    )
                },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Pink,
                    selectedLabelColor = Color(0xFF210012),
                    selectedLeadingIconColor = Color(0xFF210012),
                    containerColor = Panel2,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent,
                )
            )
        }
    }
}

@Composable
private fun FilterPicker(filter: GuideFilter, onFilter: (GuideFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GuideFilter.entries.forEach { item ->
            val selected = item == filter
            AssistChip(
                shape = RoundedCornerShape(22.dp),
                onClick = { onFilter(item) },
                label = { Text(item.label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) Pink.copy(alpha = 0.18f) else Panel,
                    labelColor = if (selected) PinkSoft else TextSecondary,
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = if (selected) Pink.copy(alpha = 0.42f) else Color(0xFF252B3C),
                )
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

private val ChannelWidth = 118.dp
private val HalfHourWidth = 132.dp
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
    val guideEnd = guideStart.plusHours(WindowHours)
    val scroll = rememberScrollState()
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val effectiveHalfHourWidth = if (landscape) 144.dp else HalfHourWidth
    val pixelsPerMinute = effectiveHalfHourWidth.value / 30f

LaunchedEffect(selectedDay, guideStart, jumpTarget) {
        val target = when (jumpTarget) {
            GuideJumpTarget.NOW -> if (selectedDay == now.toLocalDate()) now else guideStart
            GuideJumpTarget.TONIGHT -> selectedDay.atTime(18, 0).atZone(zone)
            GuideJumpTarget.TOMORROW -> selectedDay.atStartOfDay(zone)
        }

        val minutesFromStart = Duration.between(guideStart, target)
            .toMinutes()
            .coerceIn(0, WindowHours * 60)

        val targetPosition = minutesFromStart * pixelsPerMinute
        val visibleGuideWidth = configuration.screenWidthDp - ChannelWidth.value
        val centreOffset = visibleGuideWidth / 2f

        scroll.scrollTo(
            (targetPosition - centreOffset)
                .toInt()
                .coerceAtLeast(0)
        )
    }
    val totalWidth = effectiveHalfHourWidth * (WindowHours.toInt() * 2)

    Column(Modifier.fillMaxSize()) {
        TimelineHeader(guideStart, totalWidth, effectiveHalfHourWidth, scroll)

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
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF0C101C))
    ) {
        Box(
            Modifier
                .width(ChannelWidth)
                .fillMaxHeight()
                .padding(start = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Channels", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scroll)
        ) {
            Row(Modifier.width(totalWidth).fillMaxHeight()) {
                repeat((WindowHours * 2).toInt()) { index ->
                    val time = guideStart.plusMinutes(index * 30L)
                    Box(
                        Modifier
                            .width(halfHourWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Midnight)
    ) {
        ChannelCell(channel)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scroll)
        ) {
            Box(
                Modifier
                    .width(totalWidth)
                    .fillMaxHeight()
            ) {
                if (programmes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "No listings available yet",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                programmes.forEach { p ->
                    val visibleStart = if (p.start.isBefore(guideStart)) guideStart else p.start
                    val visibleStop = if (p.stop.isAfter(guideEnd)) guideEnd else p.stop
                    val offsetMinutes = Duration.between(guideStart, visibleStart).toMinutes().coerceAtLeast(0)
                    val durationMinutes = Duration.between(visibleStart, visibleStop).toMinutes().coerceAtLeast(5)
                    val x = (offsetMinutes * pixelsPerMinute).dp
                    val width = max(56f, durationMinutes * pixelsPerMinute).dp

                    ProgrammeCard(
                        programme = p,
                        isFavourite = favouriteShows.any { sameShowTitle(it, p.title) },
                        modifier = Modifier
                            .offset(x = x, y = 7.dp)
                            .width(width - 3.dp)
                            .height(70.dp),
                        onClick = { onProgramme(p) }
                    )
                }

                if (now.toLocalDate() == guideStart.toLocalDate() &&
                    !now.isBefore(guideStart) && now.isBefore(guideEnd)
                ) {
                    val nowMinutes = Duration.between(guideStart, now).toMinutes()
                    Box(
                        Modifier
                            .offset(x = (nowMinutes * pixelsPerMinute).dp)
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(PinkSoft)
                    )
                    Text(
                        "NOW",
                        color = PinkSoft,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.offset(
                            x = ((nowMinutes * pixelsPerMinute) + 5f).dp,
                            y = 2.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelCell(channel: TvChannel) {

    Row(
        modifier = Modifier
            .width(ChannelWidth)
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF171D30))
                .border(
                    1.dp,
                    Color(0xFF252C40),
                    RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {

            if (!channel.icon.isNullOrBlank()) {

                SubcomposeAsyncImage(
                    model = channel.icon,
                    contentDescription = channel.name,
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
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Pink
                            )
                        }

                        else -> {
                            LogoFallback(channel.name)
                        }
                    }
                }

            } else {
                LogoFallback(channel.name)
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = channel.name,
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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

@Composable
private fun ProgrammeCard(
    programme: Programme,
    isFavourite: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val now = ZonedDateTime.now()
    val live = !now.isBefore(programme.start) && now.isBefore(programme.stop)
    val cardBrush = if (live) {
        Brush.horizontalGradient(listOf(Color(0xFF55336F), Color(0xFF7A315D)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF1C2941), Color(0xFF20283B)))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardBrush)
            .border(
                width = 1.dp,
                color = if (live) Pink.copy(alpha = .42f) else Color(0xFF2B3650),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                programme.title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Text(
                        "NOW",
                        color = PinkSoft,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    programme.start.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        if (isFavourite) {
            Text(
                "♥",
                color = PinkSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd)
            )
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
            shape = RoundedCornerShape(22.dp)
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
                        if (ch != null && start != null && stop != null && !title.isNullOrBlank()) {
                            programmes += Programme(
                                channelId = ch,
                                start = start.withZoneSameInstant(ZoneId.systemDefault()),
                                stop = stop.withZoneSameInstant(ZoneId.systemDefault()),
                                title = title,
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
