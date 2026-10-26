package cn.nascp.clash.update

/** Only explicit semantic version tags may advertise an application update. */
internal data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long, val suffix: List<String>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        for ((a, b) in listOf(major to other.major, minor to other.minor, patch to other.patch)) {
            if (a != b) return a.compareTo(b)
        }
        if (suffix.isEmpty() != other.suffix.isEmpty()) return if (suffix.isEmpty()) 1 else -1
        for (i in 0 until minOf(suffix.size, other.suffix.size)) {
            val a = suffix[i]; val b = other.suffix[i]
            val an = a.all(Char::isDigit); val bn = b.all(Char::isDigit)
            val order = when {
                an && bn -> if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
                an -> -1
                bn -> 1
                else -> a.compareTo(b)
            }
            if (order != 0) return order
        }
        return suffix.size.compareTo(other.suffix.size)
    }

    companion object {
        fun parse(value: String): ReleaseVersion? {
            val match = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z.-]+)?$").matchEntire(value) ?: return null
            val parts = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
            val suffix = match.groupValues[4].takeIf { it.isNotBlank() }?.split('.') ?: emptyList()
            if (suffix.any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
            return ReleaseVersion(parts[0], parts[1], parts[2], suffix)
        }
    }
}
