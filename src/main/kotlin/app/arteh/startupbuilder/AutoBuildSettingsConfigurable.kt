package app.arteh.startupbuilder

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.util.maximumHeight
import com.intellij.ui.util.maximumWidth
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class AutoBuildSettingsConfigurable : Configurable {
    private val audioCombo = ComboBox(AudioDone.entries.toTypedArray())
    private val gitCombo = ComboBox(GitMergeStrategy.entries.toTypedArray())

    private var column = JPanel()

    init {
        gitCombo.maximumWidth = 120
        audioCombo.maximumWidth = 120


        column = column.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        val audioRow = JPanel(FlowLayout(FlowLayout.LEFT))
        audioRow.add(JLabel("Play audio after done:"))
        audioRow.add(audioCombo)
        audioRow.maximumHeight = 120

        val gitRow = JPanel(FlowLayout(FlowLayout.LEFT))
        gitRow.add(JLabel("Git Sync Strategy:"))
        gitRow.add(gitCombo)
        gitRow.maximumHeight = 120

        column.add(audioRow)
        column.add(gitRow)

        column.maximumHeight = 300

        audioCombo.addActionListener {
            val selected = audioCombo.selectedItem as AudioDone

            ExtraStep().maybePlaySound(selected)
        }
    }

    override fun getDisplayName(): String = "Startup Builder"

    override fun createComponent(): JComponent = column

    override fun isModified(): Boolean {
        return audioCombo.selectedItem != 0 || gitCombo.selectedItem != 0
    }

    override fun apply() {
        AutoBuildSettingsState.getInstance().state.playSound = audioCombo.selectedItem as AudioDone
        AutoBuildSettingsState.getInstance().state.gitMerge = gitCombo.selectedItem as GitMergeStrategy
    }

    override fun reset() {
        audioCombo.selectedItem = AudioDone.NONE
        gitCombo.selectedItem = GitMergeStrategy.NONE
    }
}