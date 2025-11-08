package app.arteh.startupbuilder

import com.intellij.openapi.options.Configurable
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class AutoBuildSettingsConfigurable : Configurable {
    private val checkbox1 = JCheckBox("Play sound after auto build")
    private val checkbox2 = JCheckBox("Rebase data from Git")
    private val panel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(checkbox1)
        add(checkbox2)
    }

    override fun getDisplayName(): String = "Startup Builder"

    override fun createComponent(): JComponent = panel

    override fun isModified(): Boolean {
        return checkbox1.isSelected != AutoBuildSettingsState.getInstance().state.playSound &&
                checkbox2.isSelected != AutoBuildSettingsState.getInstance().state.gitMerge
    }

    override fun apply() {
        AutoBuildSettingsState.getInstance().state.playSound = checkbox1.isSelected
        AutoBuildSettingsState.getInstance().state.gitMerge = checkbox2.isSelected
    }

    override fun reset() {
        checkbox1.isSelected = AutoBuildSettingsState.getInstance().state.playSound
        checkbox2.isSelected = AutoBuildSettingsState.getInstance().state.playSound
    }
}