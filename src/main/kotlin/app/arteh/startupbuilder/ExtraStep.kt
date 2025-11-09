package app.arteh.startupbuilder

import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class ExtraStep(private val project: Project) {

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

        val git = Git.getInstance()
        val repoManager = GitRepositoryManager.getInstance(project)
        val repositories: List<GitRepository> = repoManager.repositories

        val fetchResult = GitFetchSupport.fetchSupport(project).fetchAllRemotes(repositories)
        if (fetchResult.showNotificationIfFailed()) return

        repositories.forEach { repo ->
            val currentBranch = repo.currentBranch?.name ?: return@forEach
            if (currentBranch in listOf("master", "main")) return@forEach // skip protected branches

            val branchToMerge = "origin/${repo.currentBranch?.name ?: return@forEach}"

            if (pluginSettings.state.gitMerge) {
                val result = git.merge(repo, branchToMerge, null)
                if (!result.success()) gitFailed(repo)
            } else {
                val handler = GitLineHandler(project, repo.root, GitCommand.REBASE)
                handler.addParameters("origin/master")
                val result = git.runCommand(handler)

                if (!result.success()) gitFailed(repo)
            }
        }
    }

    private suspend fun gitFailed(repo: GitRepository) {
        withContext(Dispatchers.EDT) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Startup Buildr")
                .createNotification(
                    "Merge or rebase failed for ${repo.presentableUrl}. Please pull and resolve conflicts manually.",
                    NotificationType.WARNING
                )
                .notify(project);
        }
    }

    fun maybePlaySound() {
        if (!pluginSettings.state.playSound) return

        try {
            try {
                val resource = javaClass.getResourceAsStream("/success.wav")
                if (resource != null) {
                    // Wrap the stream so mark/reset works
                    val bufferedStream = BufferedInputStream(resource)

                    val audioInput = AudioSystem.getAudioInputStream(bufferedStream)
                    val clip: Clip = AudioSystem.getClip()
                    clip.open(audioInput)
                    clip.start()

                    audioInput.close()
                } else {
                    println("Sound resource not found!")
                }
            } catch (e: Exception) {
                println("Failed to play sound: ${e.message}")
                e.printStackTrace()
            }
        } catch (e: Exception) {
            println("Failed to play sound: ${e.message}")
        }
    }
}