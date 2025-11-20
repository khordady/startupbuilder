# StrartupBuilder
<b>StartupBuilder</b> automatically builds and updates your project as soon as it opens in Android Studio and IntelliJ IDEA.<br><br>

### <b>Features:</b><br>
• 🔄 Automatically triggers project build on startup<br>
• 🧱 Performs Git Fetch & Merge/Rebase after build<br>
• 🔔 Plays a notification sound when finished (can be disabled in settings)<br>
• ⚙️ Fully configurable via <i>Settings → Other Settings → StartupBuilder</i><br>
• 📁 Dedicated <b>Protobuf Tool Window</b> with a <b>Build button for each module</b><br><br>

### 🚀 <b>How It Works</b>
1) When you open Android Studio, Gradle starts importing the project.<br>
2) If the import is successful, the plugin automatically runs Gradle.rebuildAllModules
(the same task that runs when you press Build or Run for the first time).<br>
3) Depending on your selected setting — None, Merge, or Rebase — the plugin:<br>
A) Fetches the latest Git changes<br>
B) Applies the selected action<br>
4) If something goes wrong (no internet, merge conflict, or uncommitted changes), a notification is shown.<br>
5) Finally, the plugin tries to build the project again.

### ☕ <b>Why I Built This Plugin</b>
The very first successful build in Android Studio takes a long time.
With this plugin, you can sit back and enjoy a coffee ☕ while everything runs automatically.

For faster builds in Android Studio, add the following lines to your gradle.properties file:
```kotlin
org.gradle.configuration-cache = true
org.gradle.caching = true
```
Also make sure to enable Gradle’s parallel sync in the settings(all of them).

To create a self-published version, import it to IntelliJ IDEA and install plugin "Plugin Devkit" .

DM on LinkedIn: <a href="https://www.linkedin.com/in/aliforootanseresht/">LinkedIn</a>