package app.arteh.startupbuilder.intellij

import app.arteh.startupbuilder.ExtraStep
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

@Service(Service.Level.PROJECT)
class IntellijBuildService(private val project: Project, private val scope: CoroutineScope) {
    private val log = Logger.getInstance(IntellijBuildService::class.java)
    private val extraStep = ExtraStep(project)

    fun run() {
        val buildSystem = detectBuildSystem(project)
        log.info("Detected build system: $buildSystem")

        when (buildSystem) {
            "Gradle" -> buildGradle(project)
            "Maven" -> buildMaven(project)
            else -> buildJps(1, project)
        }
    }

    // -------------------------------
    // 🔹 Detect Build System
    // -------------------------------
    private fun detectBuildSystem(project: Project): String {
        return when {
            isGradleProject(project) -> "Gradle"
            isMavenProject(project) -> "Maven"
            else -> "JPS"
        }
    }

    private fun isGradleProject(project: Project): Boolean {
        val isGradleSupported = PluginManagerCore.isPluginInstalled(
            PluginId.getId("org.jetbrains.plugins.gradle")
        )

        val linkedProjects = GradleSettings.getInstance(project).linkedProjectsSettings

        return isGradleSupported && linkedProjects.isNotEmpty()
    }

    private fun isMavenProject(project: Project): Boolean {
        val pluginId = PluginId.getId("org.jetbrains.idea.maven")
        if (!PluginManager.isPluginInstalled(pluginId)) return false
        return project.baseDir?.findChild("pom.xml") != null
    }

    // -------------------------------
    // ⚙️ BUILD ACTIONS
    // -------------------------------

    private fun buildGradle(project: Project) {
        log.info("Running Gradle build...")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running Gradle Build") {
            override fun run(indicator: ProgressIndicator) {
                log.info("Running Gradle build...")

                val gradleSettings = GradleSettings.getInstance(project)
                val projectPath = gradleSettings.linkedProjectsSettings.firstOrNull()?.externalProjectPath

                if (projectPath == null) {
                    log.warn("No linked Gradle project found")
                    return
                }

                val taskSettings = ExternalSystemTaskExecutionSettings().apply {
                    externalProjectPath = projectPath
                    taskNames = listOf("build")
                    externalSystemIdString = GradleConstants.SYSTEM_ID.id
                }

                runGradleBuild(1, taskSettings)
            }
        })
    }

    private fun runGradleBuild(step: Int, taskSettings: ExternalSystemTaskExecutionSettings) {
        ExternalSystemUtil.runTask(
            taskSettings, DefaultRunExecutor.EXECUTOR_ID, project,
            GradleConstants.SYSTEM_ID, object : TaskCallback {
                override fun onSuccess() {
                    if (step == 1) {
                        scope.launch(Dispatchers.IO) {
                            extraStep.fetchGit()
                            runGradleBuild(2, taskSettings)
                        }
                    }
                    else extraStep.maybePlaySound()
                }

                override fun onFailure() {
                    if (step == 1) {
                        scope.launch(Dispatchers.IO) {
                            extraStep.fetchGit()
                            runGradleBuild(2, taskSettings)
                        }
                    }
                    else extraStep.maybePlaySound()
                }
            }, ProgressExecutionMode.IN_BACKGROUND_ASYNC
        )
    }

    private fun buildMaven(project: Project) {
        log.info("Running Maven build...")
        // Since MavenProjectsManager API was removed, run via command
        val basePath = project.basePath ?: return
        val processBuilder = ProcessBuilder("mvn", "clean", "package")
            .directory(File(basePath))

        runMavenBuild(1, processBuilder)
    }

    private fun runMavenBuild(step: Int, processBuilder: ProcessBuilder) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val process = processBuilder.start()
                process.inputStream.bufferedReader().forEachLine { log.info(it) }
                process.waitFor()

                if (step == 1) {
                    scope.launch(Dispatchers.IO) {
                        extraStep.fetchGit()
                        runMavenBuild(2, processBuilder)
                    }
                }
                else extraStep.maybePlaySound()
            } catch (e: Exception) {
                log.error("Maven build failed", e)
            }
        }
    }

    private fun buildJps(step: Int, project: Project) {
        log.info("Running internal (JPS) build...")

        scope.launch(Dispatchers.Default) {
            val future = ProjectTaskManager.getInstance(project).buildAllModules()

            future.onSuccess {
                scope.launch {
                    if (step == 1) {
                        withContext(Dispatchers.IO) {
                            extraStep.fetchGit()
                        }

                        buildJps(2, project)
                    }
                    else extraStep.maybePlaySound()
                }
            }
            future.onError {
                scope.launch {
                    if (step == 1) {
                        withContext(Dispatchers.IO) {
                            extraStep.fetchGit()
                        }

                        buildJps(2, project)
                    }
                    else extraStep.maybePlaySound()
                }
            }
        }
    }
}