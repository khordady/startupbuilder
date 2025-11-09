package app.arteh.startupbuilder

enum class GitMergeStrategy(val displayName: String) {
    NONE("None"),
    MERGE("Merge"),
    REBASE("Rebase");

    override fun toString() = displayName
}