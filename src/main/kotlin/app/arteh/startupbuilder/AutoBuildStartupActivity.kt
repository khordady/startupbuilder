package app.arteh.startupbuilder

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class AutoBuildStartupActivity : ProjectActivity {

    private val log = Logger.getInstance("AutoBuildStartup")

    override suspend fun execute(project: Project) {
        log.info("AutoBuildOnStartup: Project opened (StartupActivity).")
    }
}