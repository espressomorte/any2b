package ua.any2b.tvplayer.data

import ua.any2b.core.toloka.WorkerClient

/** Shares Worker rate-limit cooldown across settings and title details in this process. */
object WorkerConnections {
    val client: WorkerClient = WorkerClient()
}
