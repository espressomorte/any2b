package ua.any2b.cli

/** Separates the command name from positional arguments before command execution. */
internal data class CliArguments(val command: String, val values: List<String>) {
    companion object {
        fun parse(arguments: Array<String>): CliArguments = CliArguments(
            command = arguments.firstOrNull()?.lowercase() ?: "menu",
            values = arguments.drop(1),
        )
    }
}
