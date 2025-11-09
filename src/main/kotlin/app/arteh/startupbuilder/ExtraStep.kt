package app.arteh.startupbuilder

import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.GitVcs
import git4idea.branch.GitRebaseParams
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class ExtraStep() {

    constructor(project: Project) : this() {
        this.project = project
    }

    private lateinit var project: Project
    private val log = Logger.getInstance(ExtraStep::class.java)
    private val pluginSettings = AutoBuildSettingsState.getInstance()

    fun isGitEnabled(): Boolean {
        val gitPluginId = PluginId.getId("Git4Idea")
        if (!PluginManager.isPluginInstalled(gitPluginId)) {
            return false
        }

        // Check if the Git VCS is active in the project
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val activeVcs = vcsManager.findVcsByName(GitVcs.NAME)
        if (activeVcs == null) {
            return false
        }

        // Check if there are mappings (roots) for Git
        val roots = vcsManager.getRootsUnderVcs(activeVcs)
        return roots.isNotEmpty()
    }

    suspend fun fetchGit() {
        if (!isGitEnabled()) return
        if (pluginSettings.state.gitMerge == GitMergeStrategy.NONE) return

        val git = Git.getInstance()
        val repoManager = GitRepositoryManager.getInstance(project)
        val repositories: List<GitRepository> = repoManager.repositories

        val fetchResult = GitFetchSupport.fetchSupport(project).fetchAllRemotes(repositories)
        if (!fetchResult.showNotificationIfFailed()) {
            gitFailed("", "Fetch command failed")
            return
        }

        repositories.forEach { repo ->
            if (repo.currentBranch == null || repo.currentBranch?.name == null) return@forEach

            val branchToMerge = "origin/${repo.currentBranch!!.name}"

            if (pluginSettings.state.gitMerge == GitMergeStrategy.MERGE) {
                val result = git.merge(repo, branchToMerge, null)
                if (!result.success()) {
                    log.info("Merge failed. ${result.exitCode}")

                    val name = if (repo.currentBranch != null) repo.currentBranch!!.name
                    else repo.presentableUrl
                    gitFailed(name, result.errorOutputAsJoinedString)

                    result.exitCode

                }
                else
                    log.info("Merge succeed")
            }
            else if (pluginSettings.state.gitMerge == GitMergeStrategy.REBASE) {
                val trackingBranch = repo.currentBranch?.findTrackedBranch(repo)
                val upstream = trackingBranch?.nameForLocalOperations ?: "origin/master"

                log.info("AutoBuildOnStartup: upstream is $upstream")

                val params = GitRebaseParams(version = GitVcs.getInstance(project).version, upstream = upstream)
                val result = git.rebase(repo, params)

                if (!result.success()) {
                    log.info("Rebase failed. ${result.exitCode}")

                    val name = if (repo.currentBranch != null) repo.currentBranch!!.name
                    else repo.presentableUrl
                    gitFailed(name, result.errorOutputAsJoinedString)
                }
                else
                    log.info("Rebase succeed")
            }
        }
    }

    private suspend fun gitFailed(repoName: String, error: String) {
        withContext(Dispatchers.EDT) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Startup Buildr")
                .createNotification(
                    "Git operations failed for $repoName. Please update project manually.\n" +
                            "Error: $error",
                    NotificationType.WARNING
                )
                .notify(project);
        }
    }

    fun maybePlaySound(audio: AudioDone = pluginSettings.state.playSound) {
        if (audio == AudioDone.NONE) return

        try {
            val resource = javaClass.getResourceAsStream("/${audio.displayName}.wav")
            if (resource != null) {
                // Wrap the stream so mark/reset works
                val bufferedStream = BufferedInputStream(resource)

                val audioInput = AudioSystem.getAudioInputStream(bufferedStream)
                val clip: Clip = AudioSystem.getClip()
                clip.open(audioInput)
                clip.start()

                audioInput.close()
            }
            else println("Sound resource not found!")
        } catch (e: Exception) {
            println("Failed to play sound: ${e.message}")
        }
    }
}