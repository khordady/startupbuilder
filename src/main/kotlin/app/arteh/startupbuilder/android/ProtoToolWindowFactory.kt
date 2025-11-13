package app.arteh.startupbuilder.android

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*

class ProtoToolWindowFactory : ToolWindowFactory {
    private val log = Logger.getInstance("AutoBuildStartup")
    private lateinit var project: Project

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        this.project = project

        val contentFactory = ContentFactory.getInstance()
        val panel = JBScrollPane(buildMainPanel())
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun buildMainPanel(): JPanel {
        val rootPanel = JPanel()
        rootPanel.layout = BoxLayout(rootPanel, BoxLayout.Y_AXIS)
        rootPanel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)

        val modules = ModuleManager.getInstance(project).modules

        log.info("ABS ToolWindow: ${modules.size}")

        modules.forEach { module ->
            val protoDirs = findProtoDirs(module)

            if (protoDirs.isEmpty()) {
                log.info("ABS ToolWindow Content: is Empty name: ${module.name}")
                return@forEach
            }

            rootPanel.add(folderRow(module, protoDirs[0].path))

            protoDirs.forEach { dir ->
                dir.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension == "proto") {
                        rootPanel.add(fileItem(file))
                    }
                }
            }
        }

        return rootPanel
    }

    private fun fileItem(file: File): JLabel {
        val fileLabel =
            JLabel(" ${file.name}", IconLoader.getIcon("/icons/proto.svg", javaClass), JLabel.LEFT)
        fileLabel.border = BorderFactory.createEmptyBorder(3, 10, 3, 10)
        fileLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // open file on click
                val vFile = LocalFileSystem.getInstance()
                    .findFileByIoFile(file)
                if (vFile != null) {
                    FileEditorManager.getInstance(project).openFile(vFile, true)
                }
            }
        })

        return fileLabel
    }

    private fun folderRow(module: Module, pathRaw: String): JPanel {
        val path = pathRaw.substring(pathRaw.indexOf("src"), pathRaw.lastIndexOf(File.separator))

        val folderRow = JPanel()
        folderRow.layout = BoxLayout(folderRow, BoxLayout.X_AXIS)
        folderRow.alignmentX = Component.LEFT_ALIGNMENT

        val moduleLabel =
            JLabel(" ${module.name} ($path)", AllIcons.Nodes.Folder, JLabel.LEFT)

        folderRow.add(moduleLabel)

        val buildButton = JButton(AllIcons.Actions.Rebuild)
        buildButton.isFocusPainted = false
        buildButton.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
        buildButton.addActionListener { executeGradleTask(module) }

        folderRow.add(buildButton)

        return folderRow
    }

    private fun executeGradleTask(module: Module) {
        val roots = ModuleRootManager.getInstance(module).contentRoots
        val basePath = roots.firstOrNull()?.path
            ?: project.basePath
            ?: return

        runGradleTask(basePath)
    }

    fun runGradleTask(modulePath: String) {
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = modulePath
            taskNames = listOf("generateDebugProto")
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
        }
        ExternalSystemUtil.runTask(
            settings,
            DefaultRunExecutor.EXECUTOR_ID,
            project,
            GradleConstants.SYSTEM_ID,
            null,
            ProgressExecutionMode.IN_BACKGROUND_ASYNC,
            false
        )
    }

    fun findProtoDirs(module: Module): List<File> {
        val roots = ModuleRootManager.getInstance(module).contentRoots
        val protoDirs = mutableListOf<File>()
        roots.forEach { vf: VirtualFile ->
            val rootPath = vf.path
            // Try typical place
            val protoPath = File(rootPath, "src/main/proto")
            if (protoPath.exists() && protoPath.isDirectory) {
                protoDirs += protoPath
            }
            // (Optional) If you support test proto or other variants:
            val testProto = File(rootPath, "src/test/proto")
            if (testProto.exists() && testProto.isDirectory) {
                protoDirs += testProto
            }
        }
        return protoDirs
    }
}