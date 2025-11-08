package app.arteh.startupbuilder

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
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

    fun fetchGit() {
        if (!isGitEnabled()) return

        val git = Git.getInstance()
        val repoManager = GitRepositoryManager.getInstance(project)
        val repositories: List<GitRepository> = repoManager.repositories

        repositories.forEach { repo ->
            val handler = if (pluginSettings.state.gitMerge)
                GitLineHandler(project, repo.root, git4idea.commands.GitCommand.MERGE)
            else
                GitLineHandler(project, repo.root, git4idea.commands.GitCommand.REBASE)
            git.runCommand(handler)
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