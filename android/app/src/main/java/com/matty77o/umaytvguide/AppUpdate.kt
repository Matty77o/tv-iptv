package com.matty77o.umaytvguide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val UPDATE_MANIFEST_URL =
    "https://github.com/Matty77o/tv-iptv/releases/latest/download/app-update.json"

private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/Matty77o/tv-iptv/releases/latest"

/** Fallback for rolling releases that contain only Umay-TV-Guide.apk. */
private suspend fun downloadLatestReleaseIfNewer(context: Context): Pair<AppUpdateInfo, File>? = withContext(Dispatchers.IO) {
    runCatching {
        val c = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "UmayTVGuide/${BuildConfig.VERSION_NAME}")
        }
        if (c.responseCode !in 200..299) {
            c.disconnect()
            return@runCatching null
        }
        val release = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        c.disconnect()
        val assets = release.optJSONArray("assets") ?: return@runCatching null
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").equals("Umay-TV-Guide.apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url").trim()
                break
            }
        }
        if (apkUrl.isBlank()) return@runCatching null

        val info = AppUpdateInfo(Int.MAX_VALUE, "latest", apkUrl, "A newer Umay TV Guide build is ready.")
        val apk = downloadUpdate(context, info)
        val archiveInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        } ?: run { apk.delete(); return@runCatching null }
        val remoteCode = if (Build.VERSION.SDK_INT >= 28) archiveInfo.longVersionCode else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }
        if (remoteCode <= BuildConfig.VERSION_CODE.toLong()) {
            apk.delete()
            return@runCatching null
        }
        val remoteName = archiveInfo.versionName ?: remoteCode.toString()
        AppUpdateInfo(remoteCode.toInt(), remoteName, apkUrl, "Umay TV Guide $remoteName is ready.") to apk
    }.getOrNull()
}

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

suspend fun checkForAppUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val c = (URL(UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "UmayTVGuide/${BuildConfig.VERSION_NAME}")
        }
        if (c.responseCode !in 200..299) {
            c.disconnect()
            return@runCatching null
        }
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val o = JSONObject(text)
        val remoteCode = o.optInt("versionCode", 0)
        val url = o.optString("apkUrl").trim()
        if (remoteCode <= BuildConfig.VERSION_CODE || url.isBlank()) return@runCatching null
        AppUpdateInfo(
            versionCode = remoteCode,
            versionName = o.optString("versionName", remoteCode.toString()),
            apkUrl = url,
            notes = o.optString("notes", "A newer Umay TV Guide build is available."),
        )
    }.getOrNull()
}

private suspend fun downloadUpdate(context: Context, info: AppUpdateInfo): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
    val target = File(dir, "Umay-TV-Guide-${info.versionName}.apk")
    if (target.exists() && target.length() > 100_000L) return@withContext target
    val temp = File(dir, "${target.name}.part")
    temp.delete()
    val c = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        useCaches = false
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "UmayTVGuide/${BuildConfig.VERSION_NAME}")
    }
    if (c.responseCode !in 200..299) error("Update download returned HTTP ${c.responseCode}")
    c.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
    c.disconnect()
    if (temp.length() < 100_000L) error("Downloaded update looks incomplete")
    if (target.exists()) target.delete()
    if (!temp.renameTo(target)) {
        temp.copyTo(target, overwrite = true)
        temp.delete()
    }
    target
}

private fun launchInstaller(context: Context, apk: File): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    return true
}



/** Manual updater used from Settings. This deliberately shows progress/results so update problems are visible. */
@Composable
fun ManualUpdateControl() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }

    Column {
        Button(
            enabled = !checking,
            onClick = {
                checking = true
                status = "Checking GitHub for updates…"
                update = null
                apk = null
                scope.launch {
                    val manifestUpdate = checkForAppUpdate()
                    if (manifestUpdate != null) {
                        update = manifestUpdate
                        status = "Update ${manifestUpdate.versionName} found — downloading…"
                        runCatching { downloadUpdate(context, manifestUpdate) }
                            .onSuccess { apk = it; status = "Update ${manifestUpdate.versionName} is ready to install." }
                            .onFailure { status = "Update found, but download failed: ${it.message ?: "unknown error"}" }
                    } else {
                        status = "Checking the latest GitHub release…"
                        val fallback = downloadLatestReleaseIfNewer(context)
                        if (fallback != null) {
                            update = fallback.first
                            apk = fallback.second
                            status = "Update ${fallback.first.versionName} is ready to install."
                        } else {
                            status = "You're up to date — v${BuildConfig.VERSION_NAME}."
                        }
                    }
                    checking = false
                }
            }
        ) { Text(if (checking) "Checking…" else "Check for updates") }

        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it)
        }
        if (checking) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator()
        }
        if (apk != null) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                val file = apk ?: return@Button
                if (!launchInstaller(context, file)) {
                    status = "Allow Umay TV Guide to install unknown apps, then return here and tap Install update."
                }
            }) { Text("Install update") }
        }
    }
}

/**
 * Fast household update flow:
 * - checks GitHub on launch and whenever the app comes back to the foreground;
 * - silently pre-downloads a newer APK to cache;
 * - only interrupts once the APK is actually ready to install;
 * - Android still owns the final package-install confirmation for a normal app.
 */
@Composable
fun AppUpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkGeneration by remember { mutableIntStateOf(0) }

    fun triggerCheck() { checkGeneration++ }

    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) triggerCheck()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(checkGeneration) {
        if (checking || downloading) return@LaunchedEffect
        checking = true
        val found = checkForAppUpdate()
        if (found != null && found.versionCode != update?.versionCode) {
            update = found
            error = null
            downloading = true
            runCatching { downloadUpdate(context, found) }
                .onSuccess { downloadedApk = it }
                .onFailure { error = it.message ?: "Could not download update" }
            downloading = false
        } else if (found == null) {
            // If the rolling GitHub release only has the APK (no app-update.json),
            // download that candidate once, read its embedded Android version and compare it.
            downloading = true
            val fallback = downloadLatestReleaseIfNewer(context)
            if (fallback != null) {
                update = fallback.first
                downloadedApk = fallback.second
                error = null
            }
            downloading = false
        }
        checking = false
    }

    LaunchedEffect(Unit) { triggerCheck() }
    content()

    val info = update
    if (info != null && (downloadedApk != null || error != null)) {
        AlertDialog(
            onDismissRequest = { update = null; downloadedApk = null; error = null },
            title = { Text("Umay TV Guide ${info.versionName} is ready") },
            text = {
                Column {
                    Text(error ?: info.notes)
                    if (downloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator()
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = downloadedApk != null && !downloading,
                    onClick = {
                        val apk = downloadedApk ?: return@Button
                        val launched = launchInstaller(context, apk)
                        if (!launched) {
                            error = "Allow Umay TV Guide to install unknown apps, then return here and tap Install update again."
                        }
                    }
                ) { Text(if (error == null) "Install update" else "Try install again") }
            },
            dismissButton = {
                TextButton(onClick = { update = null; downloadedApk = null; error = null }) { Text("Later") }
            }
        )
    }
}

