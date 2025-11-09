package app.arteh.startupbuilder

enum class AudioDone(val displayName: String) {
    NONE("None"),
    M1("music1"),
    M2("music2"),
    M3("music3"),
    M4("music4"),
    M5("music5"),
    M6("music6");

    override fun toString() = displayName
}