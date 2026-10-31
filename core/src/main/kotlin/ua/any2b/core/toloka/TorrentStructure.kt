package ua.any2b.core.toloka

/** Minimal bounded bencode check, including a root `info` dictionary. */
internal object TorrentStructure {
    fun valid(bytes: ByteArray): Boolean = try {
        Parser(bytes).readRoot()
    } catch (_: IllegalArgumentException) { false }

    private class Parser(private val bytes: ByteArray) {
        private var position = 0
        private var nodes = 0
        private var hasInfo = false

        fun readRoot(): Boolean {
            require(bytes.firstOrNull() == 'd'.code.toByte())
            readValue(0)
            return position == bytes.size && hasInfo
        }

        private fun readString(): ByteArray {
            val start = position
            while (position < bytes.size && bytes[position].toInt() in 48..57)
                position++
            require(start < position && position < bytes.size && bytes[position] == ':'.code.toByte())
            val count = String(bytes, start, position - start, Charsets.US_ASCII).toLongOrNull()
                ?: throw IllegalArgumentException()
            require(count in 0..(bytes.size - position - 1).toLong())
            position++
            val result = bytes.copyOfRange(position, position + count.toInt())
            position += count.toInt()
            return result
        }

        private fun readValue(depth: Int) {
            require(++nodes <= 100_000 && depth <= 64 && position < bytes.size)
            when (bytes[position].toInt().toChar()) {
                in '0'..'9' -> readString()
                'i' -> {
                    position++
                    val start = position
                    while (position < bytes.size && bytes[position] != 'e'.code.toByte()) position++
                    require(position < bytes.size)
                    val number = String(bytes, start, position - start, Charsets.US_ASCII)
                    require(number.matches(Regex("0|-?[1-9][0-9]*")))
                    position++
                }
                'l', 'd' -> {
                    val dictionary = bytes[position] == 'd'.code.toByte()
                    position++
                    while (position < bytes.size && bytes[position] != 'e'.code.toByte()) {
                        if (dictionary) {
                            val key = readString()
                            val info = depth == 0 && key.contentEquals("info".toByteArray())
                            if (info) {
                                require(!hasInfo && position < bytes.size &&
                                    bytes[position] == 'd'.code.toByte())
                                hasInfo = true
                            }
                        }
                        readValue(depth + 1)
                    }
                    require(position < bytes.size && bytes[position] == 'e'.code.toByte())
                    position++
                }
                else -> throw IllegalArgumentException()
            }
        }
    }
}
