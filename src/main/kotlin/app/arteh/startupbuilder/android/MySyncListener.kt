package app.arteh.startupbuilder.android

import app.arteh.startupbuilder.ExtraStep
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.SystemIndependent
import java.io.File

class MySyncListener : GradleSyncListenerWithRoot {
    private val log = Logger.getInstance("AutoBuildStartup")

    override fun syncSucceeded(
        project: Project,
        rootProjectPath: @SystemIndependent String
    ) {
        super.syncSucceeded(project, rootProjectPath)

        log.info("AutoBuildOnStartup: Project Synced finished.")

        startBuild(project)
    }

    override fun syncSkipped(project: Project) {
        super.syncSkipped(project)

        log.info("AutoBuildOnStartup: Project Synced skipped.")

        startBuild(project)
    }

    fun startBuild(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Auto Build & Fetch") {
                override fun run(indicator: ProgressIndicator) {
                    log.info("AutoBuildOnStartup: Building started.")

                    runBlocking {
                        delay(2000)

                        val extraStep = ExtraStep(project)

                        indicator.text = "Building project..."
                        buildProject(project)

                        indicator.text = "Fetching latest commits..."
                        extraStep.fetchGit()


                        indicator.text = "Building again..."
                        buildProject(project)

                        indicator.text = "Done!"
                        extraStep.maybePlaySound()
                    }
                }
            })
        }
    }

    suspend fun buildProject(project: Project) {
        val hasGradle = isGradleProject(project)

        if (hasGradle) {
            waitUntilProjectReadyForBuild(project)

            buildWithGradle(project, getGradleSystemId())
        }
    }

    /**
     * Runs Gradle `build` task and waits until it completes before returning.
     */
    suspend fun buildWithGradle(project: Project, systemId: ProjectSystemId) {
        val projectPath = project.basePath ?: return
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = projectPath
            taskNames = listOf("build")
            externalSystemIdString = systemId.id
        }

        val completion = CompletableDeferred<Unit>()

        val callback = object : TaskCallback {
            override fun onSuccess() {
                log.info("Gradle build succeeded")

                completion.complete(Unit)
            }

            override fun onFailure() {
                log.warn("Gradle build failed:")

                completion.complete(Unit)
            }
        }

        ExternalSystemUtil.runTask(
            settings,
            DefaultRunExecutor.EXECUTOR_ID,
            project,
            systemId,
            callback,
            ProgressExecutionMode.IN_BACKGROUND_ASYNC
        )

        completion.await()
    }

    private fun getGradleSystemId(): ProjectSystemId {
        return try {
            // Try to load the Gradle plugin constant if available
            val clazz = Class.forName("org.jetbrains.plugins.gradle.util.GradleConstants")
            val field = clazz.getDeclaredField("SYSTEM_ID")
            field.get(null) as ProjectSystemId
        } catch (e: Exception) {
            // Fallback if Gradle plugin not loaded
            ProjectSystemId("GRADLE")
        }
    }

    private fun isGradleProject(project: Project): Boolean {
        return File(project.basePath ?: return false, "build.gradle").exists() ||
                File(project.basePath ?: return false, "build.gradle.kts").exists()
    }

    suspend fun waitUntilProjectReadyForBuild(project: Project, timeoutMs: Long = 120_000L) {
        val start = System.currentTimeMillis()
        log.info("AutoBuildOnStartup: Waiting for project readiness (timeout ${timeoutMs}ms)...")

        // 1) If Android Studio GradleSyncState exists -> wait for sync to complete
        val gradleSyncStateAvailable = try {
            Class.forName("com.android.tools.idea.gradle.project.sync.GradleSyncState")
            true
        } catch (e: Exception) {
            false
        }

        if (gradleSyncStateAvailable) {
            log.info("GradleSyncState found — will wait until Android Gradle Sync completes.")
            // Use reflection to call GradleSyncState.getInstance(project).isSyncInProgress()
            try {
                val clazz = Class.forName("com.android.tools.idea.gradle.project.sync.GradleSyncState")
                val getInstance = clazz.getMethod("getInstance", Project::class.java)
                val isSyncInProgress = clazz.getMethod("isSyncInProgress")
                val isSyncNeeded = clazz.getMethod("isSyncNeeded") // optional, may not exist

                while (true) {
                    val instance = getInstance.invoke(null, project)
                    // isSyncInProgress might throw if not available; wrap
                    val inProgress = try {
                        isSyncInProgress.invoke(instance) as? Boolean ?: false
                    } catch (t: Throwable) {
                        false
                    }

                    val syncNeeded = try {
                        isSyncNeeded.invoke(instance) as? Boolean ?: false
                    } catch (_: Throwable) {
                        false
                    }

                    log.debug("GradleSyncState: inProgress=$inProgress, syncNeeded=$syncNeeded")

                    if (!inProgress) {
                        // If sync not in progress, but syncNeeded==true, it may still be queued.
                        // Wait a small grace period for project model to become available.
                        if (!syncNeeded) break
                    }

                    // timeout check
                    if (System.currentTimeMillis() - start > timeoutMs) {
                        throw IllegalStateException("Timed out waiting for Android Gradle Sync to complete.")
                    }
                    delay(1_000L)
                }

                // extra short wait to let models settle
                delay(500L)
                log.info("Android Gradle Sync completed (or not needed). Proceeding.")
                return
            } catch (t: Throwable) {
                log.warn("Error while checking GradleSyncState via reflection, falling back: ${t.message}", t)
                // fall through to general checks
            }
        }

        // 2) Fallback: wait for project initialized and indexing finished
        log.info("Falling back to general readiness checks (project initialized + not dumb).")

        withTimeout(timeoutMs) {
            while (true) {
                // Project initialized
                val initialized = try {
                    project.isInitialized
                } catch (_: Throwable) {
                    false
                }

                // Not indexing (DumbService)
                val dumb = try {
                    DumbService.isDumb(project)
                } catch (_: Throwable) {
                    false
                }

                log.debug("Project initialized=$initialized, DumbService.isDumb=$dumb")

                if (initialized && !dumb) {
                    // short additional wait to allow model loading to stabilize
                    delay(300L)
                    log.info("Project initialized and indexing finished.")
                    return@withTimeout
                }

                delay(1_000L)
            }
        }
    }
}