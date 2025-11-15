package app.arteh.startupbuilder.android

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*

class ProtoToolWindowFactory : ToolWindowFactory {
    private val log = Logger.getInstance("AutoBuildStartup")
    private lateinit var project: Project
    private val protoFolders = mutableListOf<Pair<String, JPanel>>()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        this.project = project

        val contentFactory = ContentFactory.getInstance()
        val panel = JBScrollPane(buildMainPanel())
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }


    //Jetbrains Folder related
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

    fun getPsiDirectory(project: Project, path: String): PsiDirectory? {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(path)
            ?: return null

        return PsiManager.getInstance(project).findDirectory(virtualFile)
    }


    //Actions
    private fun addFileAction(dirPath: String) {
        val fileName = Messages.showInputDialog(
            project,
            "Enter new proto filename:",
            "Create Proto File",
            null
        ) ?: return

        // Ensure ends with .proto
        val fixedName = if (fileName.endsWith(".proto")) fileName else "$fileName.proto"

        WriteCommandAction.runWriteCommandAction(project) {
            val psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(
                    fixedName,
                    PlainTextFileType.INSTANCE,       // IntelliJ Proto plugin type
                    "syntax = \"proto3\";\n\n"    // default template
                )

            val psiDir = getPsiDirectory(project, dirPath)
            if (psiDir != null) {
                psiDir.add(psiFile)
                reloadDirFiles(dirPath)
            }
            else {
                log.warn("Failed to locate proto directory as PsiDirectory")
            }
        }
    }

    private fun pasteAction(dirPath: String) {
        val psiDir = getPsiDirectory(project, dirPath)
        if (psiDir == null) return

        val project = psiDir.project

        val clipboard = CopyPasteManager.getInstance().contents ?: return

        val fileList = try {
            clipboard.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
        } catch (e: Exception) {
            null
        }

        if (fileList.isNullOrEmpty()) {
            Messages.showInfoMessage(project, "Clipboard does not contain files.", "Paste Files")
            return
        }

        val destDir = psiDir.virtualFile

        WriteCommandAction.runWriteCommandAction(project) {
            fileList.forEach { file ->
                val srcVFile = VfsUtil.findFileByIoFile(file, true)
                if (srcVFile != null) {
                    VfsUtil.copyFile(project, srcVFile, destDir)
                }
            }
            reloadDirFiles(dirPath)
        }

        VfsUtil.markDirtyAndRefresh(true, true, true, destDir)
    }

    private fun rebuildAction(module: Module) {
        val roots = ModuleRootManager.getInstance(module).contentRoots
        val basePath = roots.firstOrNull()?.path
            ?: project.basePath
            ?: return

        runGradleTask(basePath)
    }

    private fun runGradleTask(modulePath: String) {
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


    //UI
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

            protoDirs.forEach { dir ->
                val fileContainer = JPanel()
                fileContainer.layout = BoxLayout(fileContainer, BoxLayout.Y_AXIS)
                fileContainer.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
                fileContainer.add(folderRow(module, dir.path))

                dir.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension == "proto") {
                        fileContainer.add(fileItem(file))
                    }
                }

                log.info("ABS fileContainer size is ${fileContainer.size}")

                protoFolders.add(dir.path to fileContainer)
                rootPanel.add(fileContainer)

                log.info("ABS rootPanel size is ${rootPanel.size}")
            }
        }

        return rootPanel
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
//        buildButton.isFocusPainted = false // removes focus outline
        buildButton.isContentAreaFilled = false // removes background
        buildButton.isBorderPainted = false     // removes border
        buildButton.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
        buildButton.addActionListener { rebuildAction(module) }

        folderRow.add(buildButton)
        folderRow.add(Box.createHorizontalGlue())
        folderRow.add(folderPopup(pathRaw))

        return folderRow
    }

    private fun folderPopup(dirPath: String): JButton {
        val popup = JPopupMenu()

        val addFileItem = JMenuItem("Add File")
        addFileItem.addActionListener {
            addFileAction(dirPath)
        }

        val pasteFileItem = JMenuItem("Paste Files")
        pasteFileItem.addActionListener {
            pasteAction(dirPath)
        }

        val refreshItem = JMenuItem("Refresh list")
        refreshItem.addActionListener {
            reloadDirFiles(dirPath)
        }

        popup.add(addFileItem)
        popup.add(pasteFileItem)
        popup.add(refreshItem)

        val menuButton = JButton(AllIcons.General.Menu)
        menuButton.isFocusPainted = false // removes focus outline
        menuButton.isContentAreaFilled = false // removes background
        menuButton.isBorderPainted = false     // removes border
        menuButton.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
        menuButton.addActionListener { popup.show(menuButton, 0, menuButton.height) }

        return menuButton
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

    private fun reloadDirFiles(path: String) {
        val pair = protoFolders.find { item -> item.first == path }
        if (pair != null) {
            val fileContainer = pair.second

            for (i in fileContainer.componentCount - 1 downTo 1)
                fileContainer.remove(i)

            fileContainer.revalidate()
            fileContainer.repaint()

            File(path).walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "proto") {
                    fileContainer.add(fileItem(file))
                }
            }
        }
    }
}