package app.arteh.startupbuilder

import com.intellij.openapi.options.Configurable
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class AutoBuildSettingsConfigurable : Configurable {
    private val checkbox = JCheckBox("Play sound after auto build")
    private val panel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(checkbox)
    }

    override fun getDisplayName(): String = "Startup Builder"

    override fun createComponent(): JComponent = panel

    override fun isModified(): Boolean =
        checkbox.isSelected != AutoBuildSettingsState.getInstance().state.playSound

    override fun apply() {
        AutoBuildSettingsState.getInstance().state.playSound = checkbox.isSelected
    }

    override fun reset() {
        checkbox.isSelected = AutoBuildSettingsState.getInstance().state.playSound
    }
}