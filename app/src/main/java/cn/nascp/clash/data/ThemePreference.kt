package cn.nascp.clash.data

/** Retired light palettes migrate to the system light style; new installs follow the OS. */
internal object ThemePreference {
    const val DEFAULT = "system"
    fun resolve(value: String?): String = when (value) {
        "system", "light", "dark" -> value
        "plain", "sage", "pearl" -> "light"
        else -> DEFAULT
    }
}
