package app.arteh.startupbuilder.settings

import app.arteh.startupbuilder.ExtraStep
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.util.maximumHeight
import com.intellij.ui.util.maximumWidth
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class AutoBuildSettingsConfigurable : Configurable {
    private val audioCombo = ComboBox(AudioDone.entries.toTypedArray())
    private val gitCombo = ComboBox(GitMergeStrategy.entries.toTypedArray())

    private var column = JPanel()
    private var lastAudioSelected: AudioDone? = null

    init {
        gitCombo.maximumHeight = 40
        audioCombo.maximumHeight = 40

        gitCombo.maximumWidth = 120
        audioCombo.maximumWidth = 120

        column.layout = BoxLayout(column, BoxLayout.Y_AXIS)

        val audioRow = JPanel()
        audioRow.layout = BoxLayout(audioRow, BoxLayout.X_AXIS)
        audioRow.add(JLabel("Play audio after done:"))
        audioRow.add(audioCombo)

        val gitRow = JPanel()
        gitRow.layout = BoxLayout(gitRow, BoxLayout.X_AXIS)
        gitRow.add(JLabel("Git Sync Strategy:"))
        gitRow.add(gitCombo)

        column.add(audioRow)
        column.add(gitRow)

        audioCombo.addActionListener {
            val selected = audioCombo.selectedItem as AudioDone

            if (lastAudioSelected != null) {
                ExtraStep().maybePlaySound(selected)
            }

            lastAudioSelected = selected
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
        audioCombo.selectedItem = AudioDone.M1
        gitCombo.selectedItem = GitMergeStrategy.MERGE
    }
}