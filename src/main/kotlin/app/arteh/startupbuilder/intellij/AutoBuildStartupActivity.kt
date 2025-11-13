package app.arteh.startupbuilder.intellij

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class AutoBuildStartupActivity : ProjectActivity {

    private val log = Logger.getInstance("AutoBuildStartup")

    override suspend fun execute(project: Project) {
        log.info("AutoBuildOnStartup: Project opened (StartupActivity).")

        val appInfo = ApplicationInfo.getInstance()
        val build = ApplicationInfo.getInstance().build
        val appName = appInfo.versionName
        val productCode = build.productCode

        if (appName.contains("Android Studio", ignoreCase = true) || productCode.startsWith("AI")) {
            log.info("AutoBuildOnStartup: is Android studio, then leave it to sync listener.")
            return
        }
        else
            log.info("AutoBuildOnStartup: is not Android studio. then check build system")

        val intellijBuildService = project.getService(IntellijBuildService::class.java)
        intellijBuildService.run()
    }
}