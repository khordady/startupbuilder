package app.arteh.startupbuilder

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "AutoBuildSettings", storages = [Storage("autoBuildSettings.xml")])
class AutoBuildSettingsState : PersistentStateComponent<AutoBuildSettingsState.State> {

    data class State(
        var playSound: Boolean = true,
        var gitMerge: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): AutoBuildSettingsState = service()
    }
}