package app.arteh.startupbuilder.settings

enum class GitMergeStrategy(val displayName: String) {
    NONE("None"),
    MERGE("Merge"),
    REBASE("Rebase");

    override fun toString() = displayName
}