package ua.any2b.tvplayer.data

import android.content.Context
import ua.any2b.core.toloka.DirectTolokaClient
import ua.any2b.core.toloka.TolokaSearch

/** Switches between the on-device backend and the existing Worker without mixing credentials. */
object TolokaConnections {
    private val direct = DirectTolokaClient()

    fun configured(context: Context): Boolean = if (SettingsManager(context).isEncryptedConfigEnabled()) {
        TolokaCredentialStore(context).read() != null
    } else {
        SettingsManager(context).workerUrl().isNotBlank() &&
            WorkerKeyStore(context).read()?.isNotBlank() == true
    }

    suspend fun search(context: Context, query: String): TolokaSearch {
        if (SettingsManager(context).isEncryptedConfigEnabled()) {
            val credentials = TolokaCredentialStore(context).read()
                ?: throw IllegalStateException("У конфігу немає даних Toloka")
            return direct.search(credentials.username, credentials.password, query)
        }
        val url = SettingsManager(context).workerUrl()
        val key = WorkerKeyStore(context).read()
            ?: throw IllegalStateException("Налаштуйте ключ Worker")
        return WorkerConnections.client.search(url, key, query)
    }

    suspend fun torrent(context: Context, topicId: String): ByteArray {
        if (SettingsManager(context).isEncryptedConfigEnabled()) {
            val credentials = TolokaCredentialStore(context).read()
                ?: throw IllegalStateException("У конфігу немає даних Toloka")
            return direct.torrent(credentials.username, credentials.password, topicId)
        }
        val url = SettingsManager(context).workerUrl()
        val key = WorkerKeyStore(context).read()
            ?: throw IllegalStateException("Налаштуйте ключ Worker")
        return WorkerConnections.client.torrent(url, key, topicId)
    }

    suspend fun checkDirect(context: Context): Boolean {
        val credentials = TolokaCredentialStore(context).read()
            ?: throw IllegalStateException("У конфігу немає даних Toloka")
        return direct.check(credentials.username, credentials.password)
    }
}
