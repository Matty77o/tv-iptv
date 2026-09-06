package com.matty77o.umaytvguide

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
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

class MainActivity : ComponentActivity() {
    private val reminderOpenRequest = mutableStateOf<ReminderOpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var guide by remember { mutableStateOf<GuideData?>(null) }
    var channelConfig by remember { mutableStateOf(DefaultChannelConfig) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var filter by remember { mutableStateOf(GuideFilter.ALL) }
    var selectedProgramme by remember { mutableStateOf<Programme?>(null) }
    var lastUpdated by remember { mutableStateOf<LocalTime?>(null) }
    val context = LocalContext.current
    var favouriteShows by remember { mutableStateOf(loadFavouriteShows(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(refreshToken) {
        loading = true
        error = null
        try {
            guide = withContext(Dispatchers.IO) { XmlTvRepository.load(GUIDE_URL) }

            channelConfig = withContext(Dispatchers.IO) {
                try {
                    ChannelConfigRepository.load(CHANNEL_CONFIG_URL)
                } catch (_: Throwable) {
                    DefaultChannelConfig
                }
            }

            lastUpdated = LocalTime.now()
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    LaunchedEffect(guide, favouriteShows) {
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
            .filter {
                request.channelId.isBlank() ||
                    it.channelId == request.channelId
            }
            .minByOrNull { programme ->
                if (request.startEpochMillis > 0L) {
                    kotlin.math.abs(
                        programme.start.toInstant().toEpochMilli() -
                            request.startEpochMillis
                    )
                } else {
                    0L
                }
            }

        if (match != null) {
            selectedDay = match.start.toLocalDate()
            selectedProgramme = match
        }

        onReminderConsumed()
    }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Umay",
                                color = PinkSoft,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "TV Guide",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("♥", color = Pink, fontSize = 18.sp)
                        }
                        Text(
                            lastUpdated?.let {
                                "Guide refreshed ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                            } ?: "Your family TV guide",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = { refreshToken++ },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Panel2,
                            contentColor = PinkSoft
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh guide")
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Midnight, Color(0xFF0C1120), Midnight)
                    )
                )
        ) {
            when {
                loading && guide == null -> LoadingView()
                error != null && guide == null -> ErrorView(error!!) { refreshToken++ }
                guide != null -> {
                    GuideContent(
                        guide = guide!!,
                        channelConfig = channelConfig,
                        selectedDay = selectedDay,
                        onSelectedDay = { selectedDay = it },
                        filter = filter,
                        onFilter = { filter = it },
                        selectedProgramme = selectedProgramme,
                        favouriteShows = favouriteShows,
                        onProgramme = { selectedProgramme = it },
                    )

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
        ModalBottomSheet(
            onDismissRequest = { selectedProgramme = null },
            containerColor = Panel,
            contentColor = TextPrimary,
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = Color(0xFF656A7A))
            }
        ) {
            val isFavourite = favouriteShows.any { sameShowTitle(it, programme.title) }
            ProgrammeSheet(
                programme = programme,
                channel = guide?.channels?.firstOrNull { it.id == programme.channelId },
                isFavourite = isFavourite,
                onToggleFavourite = {
                    val title = programme.title.trim()
                    val updated = if (isFavourite) {
                        favouriteShows.filterNot { sameShowTitle(it, title) }.toSet()
                    } else {
                        favouriteShows + title
                    }

                    favouriteShows = updated
                    saveFavouriteShows(context, updated)

                    if (!isFavourite) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        ProgrammeReminderScheduler.scheduleForTitle(
                            context = context,
                            title = title,
                            programmes = guide?.programmes.orEmpty(),
                            channels = guide?.channels.orEmpty(),
                        )
                    } else {
                        ProgrammeReminderScheduler.cancelForTitle(
                            context = context,
                            title = title,
                            programmes = guide?.programmes.orEmpty(),
                        )
                    }
                },
                onClose = { selectedProgramme = null }
            )
        }
    }
}

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

private val ChannelWidth = 150.dp
private val HalfHourWidth = 126.dp
private const val WindowHours = 6L

@Composable
private fun TvGrid(
    channels: List<TvChannel>,
    programmes: List<Programme>,
    selectedDay: LocalDate,
    favouriteShows: Set<String>,
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
    val pixelsPerMinute = HalfHourWidth.value / 30f
    val configuration = LocalConfiguration.current

LaunchedEffect(selectedDay, guideStart) {
    if (selectedDay == now.toLocalDate()) {

        val minutesFromStart =
            Duration.between(guideStart, now).toMinutes()

        val nowPosition =
            minutesFromStart * pixelsPerMinute

        // Width of the actual programme area, excluding channel names
        val visibleGuideWidth =
            configuration.screenWidthDp - ChannelWidth.value

        // Put NOW roughly in the middle of the visible programme grid
        val centreOffset =
            visibleGuideWidth / 2f

        scroll.scrollTo(
            (nowPosition - centreOffset)
                .toInt()
                .coerceAtLeast(0)
        )
    }
}
    val totalWidth = HalfHourWidth * (WindowHours.toInt() * 2)

    Column(Modifier.fillMaxSize()) {
        TimelineHeader(guideStart, totalWidth, scroll)

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
                            .width(HalfHourWidth)
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
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Pink)
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
            .padding(horizontal = 10.dp, vertical = 10.dp),
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


private fun normaliseShowTitle(title: String): String =
    title.trim().lowercase(Locale.ROOT)

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
    ) {
        val now = System.currentTimeMillis()
        programmes
            .filter { sameShowTitle(it.title, title) }
            .filter { it.start.toInstant().toEpochMilli() > now }
            .forEach { programme ->
                val channelIconUrl = channels
                    .firstOrNull { it.id == programme.channelId }
                    ?.icon

                scheduleAlarm(
                    context = context,
                    programme = programme,
                    triggerAtMillis = programme.start.toInstant().toEpochMilli() - TEN_MINUTES_MS,
                    kind = ProgrammeReminderReceiver.KIND_SOON,
                    channelIconUrl = channelIconUrl,
                )
                scheduleAlarm(
                    context = context,
                    programme = programme,
                    triggerAtMillis = programme.start.toInstant().toEpochMilli(),
                    kind = ProgrammeReminderReceiver.KIND_NOW,
                    channelIconUrl = channelIconUrl,
                )
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
            shape = RoundedCornerShape(16.dp)
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
    fun load(url: String): List<ChannelConfig> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "UmayTVGuide/1.3")
        connection.instanceFollowRedirects = true

        try {
            val json = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

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
        } finally {
            connection.disconnect()
        }
    }
}

object XmlTvRepository {
    fun load(url: String): GuideData {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.setRequestProperty("User-Agent", "UmayTVGuide/1.0")
        connection.instanceFollowRedirects = true

        try {
            val raw = BufferedInputStream(connection.inputStream)
            val input = if (
                url.endsWith(".gz", ignoreCase = true) ||
                connection.contentEncoding?.contains("gzip", ignoreCase = true) == true
            ) GZIPInputStream(raw) else raw

            return input.use { XmlTvParser.parse(it) }
        } finally {
            connection.disconnect()
        }
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
                    "icon" -> if (channelId != null) {
                        channelIcon = parser.getAttributeValue(null, "src")
                    }
                    "programme" -> {
                        pChannel = parser.getAttributeValue(null, "channel")
                        pStart = parseXmlTvTime(parser.getAttributeValue(null, "start"))
                        pStop = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
                        pTitle = null
                        pDesc = null
                        pCategory = null
                    }
                    "title" -> if (pChannel != null) pTitle = parser.nextText()
                    "desc" -> if (pChannel != null) pDesc = parser.nextText()
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
