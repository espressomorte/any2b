package ua.any2b.tvplayer.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class ExternalVideoPlayer(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val mimeType: String,
)

class ExternalVideoPlayers(private val context: Context) {
    private val packageManager = context.packageManager

    fun available(): List<ExternalVideoPlayer> =
        (forUrl("https://example.com/video.m3u8") + forUrl("http://example.com/video.m3u8"))
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

    fun forUrl(url: String): List<ExternalVideoPlayer> {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return emptyList()

        return MIME_TYPES.flatMap { mimeType ->
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    if (activity.packageName == context.packageName) return@mapNotNull null
                    ExternalVideoPlayer(
                        packageName = activity.packageName,
                        label = info.loadLabel(packageManager).toString().ifBlank { activity.packageName },
                        component = ComponentName(activity.packageName, activity.name),
                        mimeType = mimeType,
                    )
                }
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun intentFor(url: String, packageName: String): Intent? {
        val player = forUrl(url).firstOrNull { it.packageName == packageName } ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(url), player.mimeType)
            .setComponent(player.component)
    }

    fun intentForVideo(url: String, packageName: String): Intent? {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return null
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
        val info = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo?.packageName == packageName }?.activityInfo ?: return null
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
            .setComponent(ComponentName(info.packageName, info.name))
    }

    private companion object {
        val MIME_TYPES = listOf("application/vnd.apple.mpegurl", "application/x-mpegURL", "video/*")
    }
}
