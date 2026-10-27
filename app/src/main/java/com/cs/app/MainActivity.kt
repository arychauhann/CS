package com.cs.app

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val Paper = Color(0xFFF0F3F8)
private val Card = Color(0xFFFCFDFE)
private val Ink = Color(0xFF17243A)
private val Muted = Color(0xFF5D6B80)
private val Blue = Color(0xFF2458D3)
private val Cyan = Color(0xFF42C7E8)
private val Line = Color(0xFFD9E1EC)
private val Console = Color(0xFF111C2C)
private val Green = Color(0xFF167A58)
private val Amber = Color(0xFF9A5B0A)
private val Red = Color(0xFFB33D4A)
private val Midnight = Color(0xFF101B2B)
private const val BASE = "https://cs.up.railway.app/api/mobile/v1"

@OptIn(ExperimentalTextApi::class)
private fun csFontFamily(): FontFamily {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return FontFamily.SansSerif
    return FontFamily(
        Font(
            R.font.inter_variable,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400), FontVariation.width(100f))
        ),
        Font(
            R.font.inter_variable,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500), FontVariation.width(100f))
        ),
        Font(
            R.font.inter_variable,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.width(100f))
        ),
        Font(
            R.font.inter_variable,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.width(100f))
        ),
        Font(
            R.font.inter_variable,
            weight = FontWeight.Black,
            variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.width(100f))
        )
    )
}
private val CsFontFamily by lazy { csFontFamily() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(240,243,248)
        window.navigationBarColor = android.graphics.Color.rgb(240,243,248)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { CSApp() }
    }
}

private class Api(private val tokenProvider: () -> String) {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    suspend fun call(path: String, method: String = "GET", body: String? = null, contentType: String = "application/json", extraHeaders: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(BASE + path).header("Accept", "application/json")
        val token = tokenProvider()
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        extraHeaders.forEach { (key, value) -> builder.header(key, value) }
        if (method == "GET" || method == "DELETE") {
            if (method == "DELETE" && body != null) builder.delete(body.toRequestBody(contentType.toMediaType())) else builder.method(method, null)
        } else builder.method(method, (body ?: "{}").toRequestBody(contentType.toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401) throw ApiException("Session expired. Please sign in again.", true)
                val message = try { val j = JSONObject(text); j.optString("error", j.optString("message", "Request failed (${response.code})")) } catch (_: Exception) { "Request failed (${response.code}): ${text.take(180)}" }
                throw ApiException(message)
            }
            text
        }
    }
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
private class ApiException(message: String, val expired: Boolean = false) : Exception(message)
private fun jstr(obj: JSONObject, vararg keys: String): String { for (k in keys) if (obj.has(k) && !obj.isNull(k)) return obj.optString(k); return "" }
private fun rootArray(raw: String, vararg keys: String): JSONArray {
    val obj = JSONObject(raw)
    for (key in keys) { val a = obj.optJSONArray(key); if (a != null) return a }
    return JSONArray()
}
private fun isGitHubRepositoryUrl(value: String): Boolean {
    val uri = try { URI(value.trim()) } catch (_: Exception) { return false }
    val host = uri.host?.lowercase() ?: return false
    val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
    return uri.scheme.equals("https", ignoreCase = true) &&
        (host == "github.com" || host == "www.github.com") &&
        uri.userInfo == null &&
        segments.size >= 2
}

private data class RuntimeLogEntry(val timestamp: String, val stream: String, val message: String)

private fun runtimeLogEntries(raw: String): List<RuntimeLogEntry> {
    if (raw.isBlank()) return emptyList()
    val root = try { JSONObject(raw) } catch (_: Exception) { null }
    val lines = root?.optJSONArray("lines")
        ?: root?.optJSONObject("data")?.optJSONArray("lines")
        ?: root?.optJSONObject("logs")?.optJSONArray("lines")
    if (lines != null) {
        return (0 until lines.length()).mapNotNull { index ->
            val line = lines.optJSONObject(index)
            if (line == null) {
                lines.optString(index).takeIf { it.isNotBlank() }
                    ?.let { RuntimeLogEntry("", "stdout", it) }
            } else {
                val message = jstr(line, "message", "text", "log", "output")
                message.takeIf { it.isNotBlank() }?.let {
                    RuntimeLogEntry(jstr(line, "time", "timestamp", "at"), jstr(line, "stream", "level"), it)
                }
            }
        }.takeLast(100)
    }
    val readable = root?.let { jstr(it, "logs", "output", "message", "text") }.orEmpty()
    return readable.ifBlank { raw }.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
        .takeLast(100)
        .map { RuntimeLogEntry("", "stdout", it) }
}

private fun statusLabel(raw: String): String {
    if (raw.isBlank()) return "Checking…"
    val root = try { JSONObject(raw) } catch (_: Exception) { null }
    val statusObject = root?.optJSONObject("status")
        ?: root?.optJSONObject("data")?.optJSONObject("status")
        ?: root?.optJSONObject("data")
    val direct = jstr(root ?: JSONObject(), "status", "state", "botStatus")
    val candidate = direct.takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }
        ?: jstr(statusObject ?: JSONObject(), "status", "state", "name")
    val value = candidate.ifBlank {
        when {
            root?.has("running") == true -> if (root.optBoolean("running")) "running" else "stopped"
            root != null -> jstr(root, "message", "error", "detail").ifBlank { "Unknown" }
            else -> raw.take(80)
        }
    }.replace('_', ' ').replace('-', ' ').trim()
    return value.split(Regex("\\s+")).joinToString(" ") { part ->
        part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun statusColor(status: String): Color {
    val value = status.lowercase()
    return when {
        listOf("running", "online", "active", "started", "healthy").any { value.contains(it) } -> Green
        listOf("error", "failed", "crash", "blocked").any { value.contains(it) } -> Red
        listOf("starting", "stopping", "restarting", "pending", "deploy", "wait").any { value.contains(it) } -> Amber
        listOf("stopped", "offline", "idle").any { value.contains(it) } -> Muted
        else -> Muted
    }
}

private fun statusMatches(action: String, status: String): Boolean {
    val value = status.lowercase()
    return when (action) {
        "start", "restart" -> listOf("running", "online", "active", "started", "healthy").any { value.contains(it) }
        "stop" -> listOf("stopped", "offline", "idle", "not running").any { value.contains(it) }
        else -> false
    }
}

private fun projectTabIcon(tab: String): ImageVector = when (tab) {
    "Bot" -> Icons.Outlined.SmartToy
    "Shell" -> Icons.Outlined.Terminal
    "Files" -> Icons.Outlined.FolderOpen
    "Source" -> Icons.Outlined.Source
    "Issues" -> Icons.Outlined.BugReport
    else -> Icons.Outlined.LockOpen
}

private val GitHubMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "GitHub",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M12 .5C5.65 .5.5 5.65.5 12c0 5.1 3.3 9.4 7.85 10.94.58.1.79-.25.79-.56v-2.17c-3.19.69-3.86-1.35-3.86-1.35-.53-1.34-1.28-1.7-1.28-1.7-1.04-.71.08-.7.08-.7 1.15.08 1.75 1.18 1.75 1.18 1.02 1.75 2.68 1.24 3.33.95.1-.74.4-1.24.73-1.53-2.55-.29-5.23-1.28-5.23-5.7 0-1.26.45-2.29 1.18-3.1-.12-.29-.51-1.47.11-3.06 0 0 .96-.31 3.15 1.18a10.9 10.9 0 015.74 0c2.19-1.49 3.15-1.18 3.15-1.18.62 1.59.23 2.77.11 3.06.74.81 1.18 1.84 1.18 3.1 0 4.43-2.68 5.41-5.24 5.69.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0023.5 12C23.5 5.65 18.35.5 12 .5z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

@Composable
private fun CSApp() {
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("cs_session", 0)
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    val api = remember(token) { Api { token } }
    var selectedProject by remember { mutableStateOf(JSONObject()) }
    var page by remember { mutableStateOf("Projects") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun logout() { token = ""; username = ""; selectedProject = JSONObject(); prefs.edit().clear().apply() }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            onPrimary = Color.White,
            secondary = Cyan,
            onSecondary = Midnight,
            tertiary = Green,
            background = Paper,
            onBackground = Ink,
            surface = Card,
            onSurface = Ink,
            surfaceVariant = Color(0xFFE8EDF5),
            onSurfaceVariant = Muted,
            outline = Line
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(15.dp),
            large = RoundedCornerShape(22.dp)
        )
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = CsFontFamily)) {
        Column(Modifier.fillMaxSize().background(Paper)) {
            Row(
                Modifier.fillMaxWidth()
                    .shadow(4.dp)
                    .background(Brush.horizontalGradient(listOf(Midnight, Color(0xFF1B2C47))))
                    .padding(horizontal = 18.dp, vertical = 11.dp)
                    .heightIn(min = 58.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF07111E))
                        .border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cs_icon),
                        contentDescription = "CS emblem",
                        modifier = Modifier.size(42.dp)
                    )
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("CS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = .5.sp)
                    Text("DEVELOPER WORKSPACE", color = Color(0xFFAABBD2), fontSize = 9.sp, letterSpacing = 1.05.sp)
                }
                if (token.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(alpha = .08f))
                            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(13.dp))
                            .clickable { logout() }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Outlined.PowerSettingsNew, contentDescription = "Sign out", tint = Color(0xFFB8D9FF), modifier = Modifier.size(16.dp))
                        Text("Sign out", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (message.isNotBlank()) {
                FeedbackBanner(message)
            }
            if (token.isBlank()) LoginView(busy, { busy = it }, { message = it }, { t, u -> token = t; username = u; prefs.edit().putString("token", t).putString("username", u).apply(); page = "Projects" }, api = Api { "" })
            else if (selectedProject.length() == 0) ProjectsView(api, busy, { busy = it }, { message = it }, { selectedProject = it; page = "Bot" }, username)
            else ProjectView(api, selectedProject, page, { page = it }, { message = it }, { selectedProject = JSONObject() }, busy, { busy = it })
        }
        }
    }
}

@Composable private fun FeedbackBanner(message: String) {
    val isError = message.startsWith("Error:")
    val tone = if (isError) Red else Green
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(if (isError) Color(0xFFFFF1F0) else Color(0xFFEDF8F1), shape)
            .border(1.dp, tone.copy(alpha = .16f), shape)
            .animateContentSize()
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(tone.copy(alpha = .10f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            message,
            color = if (isError) Color(0xFF92333D) else Color(0xFF23684D),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 10.dp).weight(1f)
        )
    }
}

@Composable
private fun LoginView(busy: Boolean, setBusy: (Boolean) -> Unit, setMessage: (String) -> Unit, onLogin: (String, String) -> Unit, api: Api) {
    var register by remember { mutableStateOf(false) }; var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var rememberMe by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.Center) {
        LoginHero()
        Text("GitHub projects, bot controls, shell access. One workspace.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 15.dp, bottom = 22.dp))
        Panel {
            Text(if (register) "GET STARTED" else "GOOD TO SEE YOU", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text(if (register) "Create your account" else "Sign in to CS", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
            Field("Username", user, { user = it }, hint = "Your username")
            Spacer(Modifier.height(12.dp)); Field("Password", pass, { pass = it }, hint = "Your password", secret = true)
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rememberMe, { rememberMe = it }, colors = CheckboxDefaults.colors(checkedColor = Blue))
                Text("Keep me signed in", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { rememberMe = !rememberMe }.padding(vertical = 12.dp))
            }
            PrimaryButton(if (busy) "Please wait…" else if (register) "Create account" else "Sign in", busy) {
                if (user.isBlank() || pass.isBlank()) setMessage("Enter your username and password.") else scope.launch {
                    setBusy(true); setMessage("")
                    try {
                        val data = JSONObject().put("username", user.trim()).put("password", pass).put("remember", rememberMe)
                        val raw = Api { "" }.call(if (register) "/auth/register" else "/auth/login", "POST", data.toString())
                        val obj = JSONObject(raw); val t = jstr(obj, "accessToken", "token", "sessionToken")
                        val u = if (jstr(obj, "user").isNotBlank()) jstr(obj, "user") else user.trim()
                        val actualUser = try { val uj = obj.optJSONObject("user"); uj?.optString("username", user.trim()) ?: user.trim() } catch (_: Exception) { user.trim() }
                        if (t.isBlank()) throw Exception("Login response did not include a session token.")
                        onLogin(t, actualUser.ifBlank { u })
                    } catch (e: Exception) { setMessage("Error: ${e.message ?: "Unable to sign in"}") } finally { setBusy(false) }
                }
            }
            Text(if (register) "Already have an account? Sign in" else "New to CS? Create account", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).clickable { register = !register }.padding(horizontal = 12.dp, vertical = 14.dp))
        }
    }
}

@Composable
private fun ProjectsView(api: Api, busy: Boolean, setBusy: (Boolean) -> Unit, msg: (String) -> Unit, open: (JSONObject) -> Unit, username: String) {
    var rawProjects by remember { mutableStateOf("") }; var newName by remember { mutableStateOf("") }; var importUrl by remember { mutableStateOf("") }; var botType by remember { mutableStateOf("goatbot") }
    var section by remember { mutableStateOf("Projects") }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = section != "Projects") {
        section = "Projects"
        msg("")
    }
    suspend fun refresh() { setBusy(true); try { rawProjects = api.call("/projects"); msg("") } catch (e: Exception) { msg("Error: ${e.message}") } finally { setBusy(false) } }
    LaunchedEffect(Unit) { refresh() }
    val arr = try { rootArray(rawProjects, "projects", "data", "items") } catch (_: Exception) { JSONArray() }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            when (section) {
                "Projects" -> {
                    ScreenHeading("PROJECT WORKSPACE", "Your projects", "Signed in as $username", Icons.Outlined.Dashboard)
                    ProjectHero(arr.length())
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Projects", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            SmallAction("Refresh") { scope.launch { refresh() } }
                        }
                        if (busy && rawProjects.isBlank()) LoadingState("Loading your projects")
                        if (!busy && rawProjects.isBlank()) {
                            ErrorState("Projects unavailable", "Check your connection and try loading them again.") {
                                scope.launch { refresh() }
                            }
                        } else if (arr.length() == 0 && rawProjects.isNotBlank()) {
                            EmptyState("No projects yet", "Create a project or import one from GitHub.")
                        }
                        for (i in 0 until arr.length()) { val p = arr.optJSONObject(i) ?: continue; ProjectRow(p) { open(p) } }
                    }
                    OutlinedButton(
                        onClick = { section = "Account" },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                    ) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Account security", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                }
                "Create" -> {
                    ScreenHeading("NEW WORKSPACE", "Create a project", "Choose a name and bot type to get started.", Icons.Outlined.AddCircleOutline)
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = Blue, modifier = Modifier.size(22.dp))
                            Text("Project details", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
                        }
                        Text("Start with a bot type. You can manage it from your project workspace.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
                        Field("Project name", newName, { newName = it }, hint = "My GoatBot")
                        Spacer(Modifier.height(12.dp))
                        Text("Bot type", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("goatbot", "instagram", "wa-goat").forEach { type ->
                                FilterChip(selected = botType == type, onClick = { botType = type }, label = { Text(type, fontSize = 11.sp, maxLines = 1) })
                            }
                        }
                        PrimaryButton("Create project", busy) {
                            val projectName = newName.trim()
                            if (projectName.isBlank()) {
                                msg("Enter a project name.")
                            } else {
                                scope.launch {
                                    setBusy(true)
                                    try {
                                        api.call("/projects", "POST", JSONObject().put("name", projectName).put("botType", botType).toString())
                                        newName = ""
                                        refresh()
                                        section = "Projects"
                                        msg("Project created.")
                                    } catch (e: Exception) {
                                        msg("Error: ${e.message}")
                                    } finally {
                                        setBusy(false)
                                    }
                                }
                            }
                        }
                    }
                }
                "Account" -> {
                    PasswordPage(api, msg, busy, setBusy, { section = "Projects" }, parentScrolls = true)
                }
                else -> {
                    ScreenHeading("GITHUB IMPORT", "Bring in a repository", "Import an existing GitHub project into your workspace.", Icons.Outlined.CloudDownload)
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(GitHubMark, contentDescription = "GitHub", tint = Ink, modifier = Modifier.size(25.dp))
                            Text("Import from GitHub", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                        }
                        Text("Paste the HTTPS link to a repository you want to add.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
                        Field("Repository URL", importUrl, { importUrl = it }, hint = "https://github.com/user/repo")
                        Text("Use a repository URL such as github.com/owner/project.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                        PrimaryButton("Import repository", busy) {
                            val repositoryUrl = importUrl.trim()
                            if (!isGitHubRepositoryUrl(repositoryUrl)) {
                                msg("Enter a valid HTTPS GitHub repository URL.")
                            } else {
                                scope.launch {
                                    setBusy(true)
                                    try {
                                        api.call("/projects/import-github", "POST", JSONObject().put("url", repositoryUrl).toString())
                                        importUrl = ""
                                        refresh()
                                        section = "Projects"
                                        msg("Repository imported.")
                                    } catch (e: Exception) {
                                        msg("Error: ${e.message}")
                                    } finally {
                                        setBusy(false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        NavigationBar(containerColor = Card, tonalElevation = 4.dp) {
            listOf(
                Triple("Projects", Icons.Outlined.FolderOpen, "Projects"),
                Triple("Create", Icons.Outlined.AddCircleOutline, "Create"),
                Triple("Import", GitHubMark, "GitHub"),
                Triple("Account", Icons.Outlined.LockOpen, "Account")
            ).forEach { (destination, icon, label) ->
                NavigationBarItem(
                    selected = section == destination,
                    onClick = { section = destination; msg("") },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label, fontSize = 11.sp) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Blue,
                        selectedTextColor = Blue,
                        indicatorColor = Color(0xFFE3EBFC),
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted
                    )
                )
            }
        }
    }
}

@Composable private fun ProjectRow(p: JSONObject, onClick: () -> Unit) {
    val projectName = jstr(p, "name").ifBlank { "Unnamed project" }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 9.dp)
            .clip(shape)
            .background(Color(0xFFF7F9FC), shape)
            .border(1.dp, Line.copy(alpha = .9f), shape)
            .clickable { onClick() }
            .animateContentSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(
                Brush.linearGradient(listOf(Color(0xFFDCE7FF), Color(0xFFD8F3F4))),
                RoundedCornerShape(15.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = "Project", tint = Blue, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(projectName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                jstr(p, "botType", "type").ifBlank { "Project" }.uppercase(),
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = .6.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            Text("Open workspace", color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
        }
        Box(
            Modifier.padding(start = 8.dp).size(36.dp)
                .background(Color(0xFFE7EEFF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ArrowForward, contentDescription = "Open $projectName", tint = Blue, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProjectView(api: Api, project: JSONObject, page: String, setPage: (String) -> Unit, msg: (String) -> Unit, back: () -> Unit, busy: Boolean, setBusy: (Boolean) -> Unit) {
    val id = jstr(project, "id", "_id", "projectId"); val name = jstr(project, "name").ifBlank { "Project" }
    var moreExpanded by remember { mutableStateOf(false) }
    var unsavedFileChanges by remember(id) { mutableStateOf(false) }
    var showLeaveProjectDialog by remember(id) { mutableStateOf(false) }
    val pageStateHolder = rememberSaveableStateHolder()
    val morePages = setOf("Source", "Issues", "Password")
    fun requestProjectExit() {
        if (unsavedFileChanges) showLeaveProjectDialog = true else back()
    }
    BackHandler { requestProjectExit() }
    Column(Modifier.fillMaxSize()) {
        Surface(color = Midnight, shadowElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Midnight, Color(0xFF172943))))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .heightIn(min = 62.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { requestProjectExit() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to projects", tint = Color(0xFFD5E3F5))
                }
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        jstr(project, "botType", "type").ifBlank { "PROJECT WORKSPACE" }.uppercase(),
                        color = Color(0xFF9EB3CF),
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(Cyan, page, onDarkSurface = true)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            pageStateHolder.SaveableStateProvider(page) {
                when (page) {
                    "Bot" -> BotPage(api, id, msg, busy, setBusy)
                    "Shell" -> ShellPage(api, id, msg, busy, setBusy)
                    "Files" -> FilesPage(api, id, msg, busy, setBusy) { unsavedFileChanges = it }
                    "Source" -> SourcePage(api, id, msg, busy, setBusy)
                    "Issues" -> IssuesPage(api, id, msg, busy, setBusy)
                    "Password" -> PasswordPage(api, msg, busy, setBusy, ::requestProjectExit)
                }
            }
        }
        NavigationBar(containerColor = Midnight, tonalElevation = 4.dp) {
            listOf("Bot", "Shell", "Files").forEach { destination ->
                NavigationBarItem(
                    selected = page == destination,
                    onClick = { setPage(destination); moreExpanded = false },
                    icon = { Icon(projectTabIcon(destination), contentDescription = destination) },
                    label = { Text(destination, fontSize = 10.sp) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Cyan,
                        selectedTextColor = Color.White,
                        indicatorColor = Color(0xFF203653),
                        unselectedIconColor = Color(0xFF9EB0C8),
                        unselectedTextColor = Color(0xFFB6C4D6)
                    )
                )
            }
            NavigationBarItem(
                selected = page in morePages,
                onClick = { moreExpanded = true },
                icon = {
                    Box {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "More project sections")
                        DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                            listOf("Source", "Issues", "Password").forEach { destination ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(projectTabIcon(destination), contentDescription = null, modifier = Modifier.size(19.dp))
                                            Text(destination, modifier = Modifier.padding(start = 12.dp))
                                        }
                                    },
                                    onClick = { setPage(destination); moreExpanded = false }
                                )
                            }
                        }
                    }
                },
                label = { Text(if (page in morePages) page else "More", fontSize = 10.sp) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cyan,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(0xFF203653),
                    unselectedIconColor = Color(0xFF9EB0C8),
                    unselectedTextColor = Color(0xFFB6C4D6)
                )
            )
        }
        if (showLeaveProjectDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveProjectDialog = false },
                icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = Amber) },
                title = { Text("Leave with unsaved edits?") },
                text = { Text("Your file changes have not been saved. Leave this project and discard them?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveProjectDialog = false
                        unsavedFileChanges = false
                        back()
                    }) { Text("Discard and leave", color = Red) }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveProjectDialog = false }) { Text("Stay here") }
                }
            )
        }
    }
}

@Composable private fun BotPage(api: Api, id: String, msg: (String) -> Unit, busy: Boolean, setBusy: (Boolean) -> Unit) {
    var statusRaw by remember(id) { mutableStateOf("") }
    var logsRaw by remember(id) { mutableStateOf("") }
    var refreshing by remember(id) { mutableStateOf(false) }
    var refreshError by remember(id) { mutableStateOf("") }
    var lastUpdated by remember(id) { mutableStateOf(0L) }
    var actionInProgress by remember(id) { mutableStateOf("") }
    var actionNotice by remember(id) { mutableStateOf("") }
    val refreshLock = remember(id) { Mutex() }
    val scope = rememberCoroutineScope()

    suspend fun refreshData() {
        refreshLock.withLock {
            refreshing = true
            val errors = mutableListOf<String>()
            var receivedData = false
            try {
                statusRaw = api.call("/projects/${api.enc(id)}/bot/status")
                receivedData = true
            } catch (e: Exception) {
                errors += "Status: ${e.message ?: "Unavailable"}"
            }
            try {
                logsRaw = api.call("/projects/${api.enc(id)}/bot/logs")
                receivedData = true
            } catch (e: Exception) {
                errors += "Logs: ${e.message ?: "Unavailable"}"
            }
            if (receivedData) lastUpdated = System.currentTimeMillis()
            refreshError = errors.joinToString(" · ")
            refreshing = false
        }
    }

    LaunchedEffect(id) {
        msg("")
        refreshData()
        while (true) {
            delay(5_000)
            refreshData()
        }
    }

    val currentStatus = statusLabel(statusRaw)
    val statusTint = statusColor(currentStatus)
    val displayStatus = when (actionInProgress) {
        "start" -> "Starting…"
        "stop" -> "Stopping…"
        "restart" -> "Restarting…"
        else -> currentStatus
    }
    val logEntries = remember(logsRaw) { runtimeLogEntries(logsRaw) }
    val lastUpdatedText = if (lastUpdated == 0L) "Waiting for first update" else {
        "Updated ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(lastUpdated))}"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        ScreenHeading("BOT CONTROL", "Runtime overview", "Controls and logs update automatically.", Icons.Outlined.SmartToy)

        Panel {
            val statusAccent = when (statusTint) {
                Green -> Color(0xFF63DBA6)
                Red -> Color(0xFFFF9BA3)
                Amber -> Color(0xFFFFCA83)
                else -> Color(0xFF9EB3CF)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Midnight, Color(0xFF1B2D47))))
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(56.dp).background(statusAccent, RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("BOT RUNTIME", color = Color(0xFF9EB3CF), fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.25.sp)
                    Text(displayStatus, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        if (actionInProgress.isNotBlank()) "Waiting for server confirmation"
                        else "Status checks every 5 seconds",
                        color = Color(0xFFB6C6DA),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Box(Modifier.padding(start = 12.dp)) {
                    StatusPill(
                        if (actionInProgress.isNotBlank()) Color(0xFFFFCA83) else statusAccent,
                        if (actionInProgress.isNotBlank()) "Updating" else currentStatus,
                        onDarkSurface = true
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                listOf("start", "stop", "restart").forEach { action ->
                    val actionColor = when (action) {
                        "start" -> Green
                        "stop" -> Red
                        else -> Blue
                    }
                    val actionIcon = when (action) {
                        "start" -> Icons.Outlined.PowerSettingsNew
                        "stop" -> Icons.Outlined.Stop
                        else -> Icons.Outlined.RestartAlt
                    }
                    Button(
                        onClick = {
                            if (actionInProgress.isNotBlank()) return@Button
                            scope.launch {
                                val previousStatus = statusLabel(statusRaw)
                                actionInProgress = action
                                actionNotice = "${action.replaceFirstChar { it.uppercase() }} request sent. Watching for the updated status…"
                                msg("")
                                try {
                                    setBusy(true)
                                    api.call("/projects/${api.enc(id)}/bot/$action", "POST")
                                    var confirmed = false
                                    for (attempt in 0 until 15) {
                                        refreshData()
                                        val refreshedStatus = statusLabel(statusRaw)
                                        val wasAlreadyAtTarget = statusMatches(action, previousStatus)
                                        if (statusMatches(action, refreshedStatus) && (!wasAlreadyAtTarget || attempt >= 1)) {
                                            confirmed = true
                                            break
                                        }
                                        delay(2_000)
                                    }
                                    actionNotice = if (confirmed) {
                                        "Bot ${action.replaceFirstChar { it.uppercase() }} complete · ${statusLabel(statusRaw)}"
                                    } else {
                                        "The request was accepted. CS will keep checking until the server reports the new status."
                                    }
                                } catch (e: Exception) {
                                    msg("Error: ${e.message ?: "Unable to ${action} bot"}")
                                    actionNotice = ""
                                } finally {
                                    setBusy(false)
                                    actionInProgress = ""
                                }
                            }
                        },
                        enabled = actionInProgress.isBlank() && !busy,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                    ) {
                        Icon(actionIcon, contentDescription = action, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(action.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            if (actionNotice.isNotBlank()) {
                Text(actionNotice, color = if (actionNotice.startsWith("The request")) Amber else Green, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp)
                    .background(if (refreshError.isBlank()) Color(0xFFEAF5EF) else Color(0xFFFFF2E4), RoundedCornerShape(12.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = Blue)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Box(Modifier.size(8.dp).background(if (refreshError.isBlank()) Green else Amber, RoundedCornerShape(50)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (refreshError.isBlank()) "Live updates · every 5 sec" else refreshError,
                    color = if (refreshError.isBlank()) Green else Amber,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Runtime logs", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("$lastUpdatedText  ·  ${logEntries.size} recent lines", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Box(Modifier.background(Color(0xFFEAF1FF), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text("LIVE", color = Blue, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = .8.sp)
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 13.dp).heightIn(min = 120.dp, max = 360.dp)
                    .background(Console, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF2B3D57), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState()).padding(13.dp)
            ) {
                if (logEntries.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Terminal, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
                        Text("No runtime output yet", color = Color(0xFFE4ECF7), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
                        Text("New lines will appear here automatically.", color = Color(0xFF9EB0C8), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                } else {
                    logEntries.forEachIndexed { index, entry ->
                        RuntimeLogRow(entry)
                        if (index < logEntries.lastIndex) {
                            Divider(color = Color(0xFF2B3D57), modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ShellPage(api: Api, id: String, msg: (String) -> Unit, busy: Boolean, setBusy: (Boolean) -> Unit) {
    var command by remember { mutableStateOf("") }; var output by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        ScreenHeading("SHELL TERMINAL", "Run a command", "Commands run in the project's isolated shell.", Icons.Outlined.Terminal)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 11.dp)) {
                Box(
                    Modifier.size(38.dp).background(Midnight, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null, tint = Cyan, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Command input", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("ISOLATED PROJECT SHELL", color = Muted, fontSize = 9.sp, letterSpacing = .9.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Field("Command", command, { command = it }, hint = "pwd or ls")
            PrimaryButton("Execute command", busy) {
                scope.launch {
                    setBusy(true)
                    try {
                        val raw = api.call("/projects/${api.enc(id)}/shell/command", "POST", JSONObject().put("command", command).toString())
                        output = try { jstr(JSONObject(raw), "output", "result", "message").ifBlank { raw } } catch (_: Exception) { raw }
                        msg("")
                    } catch (e: Exception) {
                        msg("Error: ${e.message}")
                    } finally {
                        setBusy(false)
                    }
                }
            }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Command output", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Box(Modifier.background(Color(0xFFE7EEF9), RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
                    Text("OUTPUT", color = Blue, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = .8.sp)
                }
            }
            Text(
                output.ifBlank { "Command output will appear here." },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = if (output.isBlank()) Color(0xFF91A4BE) else Color(0xFFE1ECFA),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    .background(Console, RoundedCornerShape(15.dp))
                    .border(1.dp, Color(0xFF2B3D57), RoundedCornerShape(15.dp))
                    .padding(14.dp)
            )
        }
    }
}

private fun isDirectoryEntry(item: JSONObject): Boolean {
    val type = jstr(item, "type").lowercase()
    return type in setOf("folder", "directory", "dir") ||
        item.optBoolean("isDirectory", false) || item.optBoolean("isFolder", false)
}

private fun fileTypeIcon(name: String, isFolder: Boolean): ImageVector {
    if (isFolder) return Icons.Outlined.Folder
    return when (name.substringAfterLast('.', "").lowercase()) {
        "kt", "java", "js", "jsx", "ts", "tsx", "py", "html", "css", "json", "xml", "yaml", "yml" -> Icons.Outlined.Code
        "png", "jpg", "jpeg", "gif", "webp", "svg" -> Icons.Outlined.Image
        "md", "txt", "log", "csv" -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
}

private fun fileTypeTint(name: String, isFolder: Boolean): Color {
    if (isFolder) return Blue
    return when (name.substringAfterLast('.', "").lowercase()) {
        "kt", "java", "js", "jsx", "ts", "tsx", "py" -> Color(0xFF7152C7)
        "html", "css", "svg" -> Color(0xFFB76721)
        "png", "jpg", "jpeg", "gif", "webp" -> Color(0xFF167A68)
        else -> Muted
    }
}

@Composable private fun FilesPage(api: Api, id: String, msg: (String) -> Unit, busy: Boolean, setBusy: (Boolean) -> Unit, onUnsavedChanges: (Boolean) -> Unit) {
    var path by rememberSaveable(id) { mutableStateOf("") }
    var raw by rememberSaveable(id) { mutableStateOf("") }
    var selected by rememberSaveable(id) { mutableStateOf("") }
    var content by rememberSaveable(id) { mutableStateOf("") }
    var originalContent by rememberSaveable(id) { mutableStateOf("") }
    var hash by rememberSaveable(id) { mutableStateOf("") }
    var newName by rememberSaveable(id) { mutableStateOf("") }
    var createFolder by rememberSaveable(id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(id) { mutableStateOf("") }
    var showCreateDialog by rememberSaveable(id) { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable(id) { mutableStateOf(false) }
    var hasLoadedTree by rememberSaveable(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasUnsavedChanges = selected.isNotBlank() && content != originalContent

    fun leaveEditor() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            selected = ""
            content = ""
        }
    }

    suspend fun loadTree() {
        setBusy(true)
        try {
            val suffix = if (path.isBlank()) "" else "?path=${api.enc(path)}"
            raw = api.call("/projects/${api.enc(id)}/files/tree$suffix")
            selected = ""
            content = ""
            originalContent = ""
            msg("")
        } catch (e: Exception) {
            msg("Error: ${e.message}")
        } finally {
            setBusy(false)
        }
    }

    LaunchedEffect(id) {
        if (!hasLoadedTree) {
            loadTree()
            hasLoadedTree = true
        }
    }
    SideEffect { onUnsavedChanges(hasUnsavedChanges) }
    BackHandler(enabled = selected.isNotBlank() || path.isNotBlank()) {
        if (selected.isNotBlank()) {
            leaveEditor()
        } else {
            path = path.substringBeforeLast('/', "")
            scope.launch { loadTree() }
        }
    }
    Column(Modifier.fillMaxSize()) {
      Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
      ) {
        ScreenHeading(
            "FILE MANAGER",
            if (selected.isBlank()) "Files & folders" else "Edit file",
            if (selected.isBlank()) "Browse, search, create and edit project files." else selected.substringAfterLast('/'),
            Icons.Outlined.FolderOpen
        )
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).background(Color(0xFFE7EEFF), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Blue, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Current folder", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(path.substringAfterLast('/').ifBlank { "Project root" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    enabled = path.isNotBlank() && selected.isBlank() && !busy,
                    onClick = {
                        path = path.substringBeforeLast('/', "")
                        searchQuery = ""
                        scope.launch { loadTree() }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                    Text(" Parent", fontSize = 11.sp)
                }
                IconButton(onClick = { scope.launch { loadTree() } }, enabled = selected.isBlank() && !busy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh folder", tint = Blue)
                }
            }
            FileBreadcrumbs(path, enabled = selected.isBlank()) { destination ->
                path = destination
                searchQuery = ""
                selected = ""
                scope.launch { loadTree() }
            }
        }
        if (selected.isBlank()) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Files & folders", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${path.split('/').count { it.isNotBlank() }} folder levels", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            newName = ""
                            createFolder = false
                            showCreateDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("  New", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                val items = try { rootArray(raw, "items", "entries", "files", "children", "tree") } catch (_: Exception) { JSONArray() }
                val visibleItems = (0 until items.length()).mapNotNull { index ->
                    items.optJSONObject(index)?.takeIf {
                        searchQuery.isBlank() || jstr(it, "name").contains(searchQuery.trim(), ignoreCase = true)
                    }
                }.sortedWith(compareBy<JSONObject> { !isDirectoryEntry(it) }.thenBy { jstr(it, "name").lowercase() })
                Field("Search this folder", searchQuery, { searchQuery = it }, hint = "Find a file or folder")
                if (busy && raw.isBlank()) {
                    LoadingState("Loading project files")
                } else if (items.length() == 0) {
                    if (raw.isBlank()) {
                        ErrorState("Folder unavailable", "Try loading this folder again.") {
                            scope.launch { loadTree() }
                        }
                    } else {
                        EmptyState("This folder is empty", "Use New to create a file or folder here.")
                    }
                }
                if (items.length() > 0 && visibleItems.isEmpty()) {
                    EmptyState("No matching items", "Try a different search or clear the field.")
                }
                visibleItems.forEach { item ->
                    val name = jstr(item, "name").ifBlank { "Unnamed" }
                    val itemPath = jstr(item, "path").ifBlank { if (path.isBlank()) name else "$path/$name" }
                    val folder = isDirectoryEntry(item)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 7.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (folder) Color(0xFFF4F7FD) else Color(0xFFF8F9FC))
                            .border(1.dp, if (folder) Color(0xFFE2E9F6) else Color(0xFFEBEEF3), RoundedCornerShape(14.dp))
                            .clickable {
                                if (folder) {
                                    path = itemPath
                                    searchQuery = ""
                                    scope.launch { loadTree() }
                                } else {
                                    selected = itemPath
                                    scope.launch {
                                        setBusy(true)
                                        try {
                                            val file = JSONObject(api.call("/projects/${api.enc(id)}/files/file?path=${api.enc(itemPath)}"))
                                            content = jstr(file, "content")
                                            originalContent = content
                                            hash = jstr(file, "hash")
                                            msg("")
                                        } catch (e: Exception) {
                                            msg("Error: ${e.message}")
                                            selected = ""
                                        } finally {
                                            setBusy(false)
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).background(fileTypeTint(name, folder).copy(alpha = .11f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                fileTypeIcon(name, folder),
                                contentDescription = null,
                                tint = fileTypeTint(name, folder),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text(name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (folder) "Folder" else name.substringAfterLast('.', "File").uppercase(), color = Muted, fontSize = 9.sp, letterSpacing = .55.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        Box(
                            Modifier.background(Color(0xFFE6EDFA), RoundedCornerShape(9.dp))
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(if (folder) "Open" else "Edit", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = if (folder) "Open folder" else "Edit file", tint = fileTypeTint(name, folder), modifier = Modifier.size(19.dp))
                    }
                }
            }
        } else {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { leaveEditor() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to folder", tint = Blue)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(selected.substringAfterLast('/'), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(selected.substringBeforeLast('/', "Project root"), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StatusPill(if (hasUnsavedChanges) Amber else Green, if (hasUnsavedChanges) "Unsaved" else "Saved")
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Code, contentDescription = null, tint = Blue, modifier = Modifier.size(17.dp))
                    Text("  Source editor", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${content.lineSequence().count()} lines", color = Muted, fontSize = 10.sp)
                }
                val lineCount = content.count { it == '\n' } + 1
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 310.dp)
                        .background(Console, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF263A54), RoundedCornerShape(16.dp)),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        Modifier.width(43.dp).background(Color(0xFF102036), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                            .padding(top = 15.dp, bottom = 15.dp, end = 9.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        repeat(lineCount) { line ->
                            Text(
                                "${line + 1}",
                                color = Color(0xFF6E829E),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 20.sp,
                                maxLines = 1
                            )
                        }
                    }
                    BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        cursorBrush = SolidColor(Color(0xFF8AB4FF)),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFE3ECF8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp)
                            .heightIn(min = 280.dp)
                    )
                }
                Text(
                    "Save your changes before leaving this file.",
                    color = Muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 9.dp)
                )
            }
        }
      }
      if (selected.isNotBlank()) {
        Surface(color = Card, shadowElevation = 7.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { leaveEditor() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("  Folder", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                    scope.launch {
                        setBusy(true)
                        try {
                            api.call(
                                "/projects/${api.enc(id)}/files/file?path=${api.enc(selected)}",
                                "PUT",
                                content,
                                "text/plain; charset=utf-8",
                                if (hash.isBlank()) emptyMap() else mapOf("X-Expected-Hash" to hash)
                            )
                            msg("File saved.")
                            selected = ""
                            loadTree()
                        } catch (e: Exception) {
                            msg("Error: ${e.message}")
                        } finally {
                            setBusy(false)
                        }
                    }
                    },
                    enabled = !busy && hasUnsavedChanges,
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasUnsavedChanges) Blue else Green,
                        disabledContainerColor = if (hasUnsavedChanges) Blue.copy(alpha = .5f) else Green.copy(alpha = .65f)
                    )
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(if (hasUnsavedChanges) Icons.Outlined.Save else Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text(if (hasUnsavedChanges) "  Save changes" else "  Saved", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
      }
      if (showCreateDialog) {
          AlertDialog(
              onDismissRequest = { if (!busy) showCreateDialog = false },
              icon = {
                  Icon(
                      if (createFolder) Icons.Outlined.CreateNewFolder else Icons.Outlined.InsertDriveFile,
                      contentDescription = null,
                      tint = Blue
                  )
              },
              title = { Text("Create ${if (createFolder) "folder" else "file"}") },
              text = {
                  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                      Text(
                          "In ${path.ifBlank { "Project root" }}",
                          color = Muted,
                          fontSize = 12.sp,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis
                      )
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                          FilterChip(
                              selected = !createFolder,
                              onClick = { createFolder = false },
                              label = { Text("New file") },
                              leadingIcon = {
                                  Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
                              }
                          )
                          FilterChip(
                              selected = createFolder,
                              onClick = { createFolder = true },
                              label = { Text("New folder") },
                              leadingIcon = {
                                  Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                              }
                          )
                      }
                      Field("Name", newName, { newName = it }, hint = if (createFolder) "components" else "new-file.js")
                  }
              },
              confirmButton = {
                  Button(
                      enabled = newName.isNotBlank() && !busy,
                      onClick = {
                          val entryName = newName.trim()
                          if (entryName.isNotBlank()) {
                              scope.launch {
                                  setBusy(true)
                                  try {
                                      api.call(
                                          "/projects/${api.enc(id)}/files/entry",
                                          "POST",
                                          JSONObject().put("parent", path).put("name", entryName)
                                              .put("type", if (createFolder) "folder" else "file").toString()
                                      )
                                      newName = ""
                                      showCreateDialog = false
                                      loadTree()
                                      msg("Entry created.")
                                  } catch (e: Exception) {
                                      msg("Error: ${e.message}")
                                  } finally {
                                      setBusy(false)
                                  }
                              }
                          }
                      }
                  ) {
                      if (busy) {
                          CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                      } else {
                          Text("Create ${if (createFolder) "folder" else "file"}")
                      }
                  }
              },
              dismissButton = {
                  TextButton(onClick = { showCreateDialog = false }, enabled = !busy) {
                      Text("Cancel")
                  }
              }
          )
      }
      if (showDiscardDialog) {
          AlertDialog(
              onDismissRequest = { showDiscardDialog = false },
              icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = Amber) },
              title = { Text("Discard unsaved changes?") },
              text = { Text("Your edits to ${selected.substringAfterLast('/')} will be lost if you leave this file.") },
              confirmButton = {
                  TextButton(onClick = {
                      showDiscardDialog = false
                      selected = ""
                      content = ""
                      originalContent = ""
                  }) { Text("Discard", color = Red) }
              },
              dismissButton = {
                  TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
              }
          )
      }
    }
}

@Composable
private fun FileBreadcrumbs(path: String, enabled: Boolean = true, onNavigate: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(11.dp))
                .background(if (path.isBlank()) Color(0xFFE4ECFC) else Color.Transparent)
                .clickable(enabled = enabled) { onNavigate("") }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = if (path.isBlank()) Blue else Muted, modifier = Modifier.size(15.dp))
            Text("Project", color = if (path.isBlank() || !enabled) Ink else Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
        var accumulated = ""
        path.split('/').filter { it.isNotBlank() }.forEach { segment ->
            accumulated = if (accumulated.isBlank()) segment else "$accumulated/$segment"
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.padding(horizontal = 2.dp).size(16.dp))
            val destination = accumulated
            val isCurrent = destination == path
            Box(
                Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (isCurrent) Color(0xFFE4ECFC) else Color.Transparent)
                    .clickable(enabled = enabled) { onNavigate(destination) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    segment,
                    color = if (isCurrent || !enabled) Ink else Blue,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SourcePage(
    api: Api,
    id: String,
    msg: (String) -> Unit,
    busy: Boolean,
    setBusy: (Boolean) -> Unit
) {
    var path by remember(id) { mutableStateOf("") }
    var raw by remember(id) { mutableStateOf("") }
    var content by remember(id) { mutableStateOf("") }
    var selected by remember(id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        setBusy(true)
        try {
            val suffix = if (path.isBlank()) "" else "?path=${api.enc(path)}"
            raw = api.call("/projects/${api.enc(id)}/shared-source/tree$suffix")
            msg("")
        } catch (e: Exception) {
            msg("Error: ${e.message ?: "Unable to load shared files"}")
        } finally {
            setBusy(false)
        }
    }

    LaunchedEffect(id) { load() }

    val items = remember(raw) {
        try {
            rootArray(raw, "items", "entries", "files", "children", "tree")
        } catch (_: Exception) {
            JSONArray()
        }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        ScreenHeading(
            "SHARED SOURCE",
            if (selected.isBlank()) "Read-only source" else "Source file",
            when {
                selected.isNotBlank() -> selected
                path.isNotBlank() -> "/$path"
                else -> "Browse the shared files for this project."
            },
            Icons.Outlined.Source
        )

        if (selected.isNotBlank()) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Midnight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Code, contentDescription = null, tint = Cyan, modifier = Modifier.size(19.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Read-only source", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(selected.substringAfterLast('/'), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    OutlinedButton(onClick = { selected = ""; content = "" }, shape = RoundedCornerShape(12.dp)) { Text("Back") }
                }
                Text(
                    content.ifBlank { "This file is empty." },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = if (content.isBlank()) Color(0xFF9EB0C8) else Color(0xFFE2EBF7),
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 13.dp)
                        .heightIn(min = 80.dp, max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .background(Console, RoundedCornerShape(15.dp))
                        .border(1.dp, Color(0xFF2B3D57), RoundedCornerShape(15.dp))
                        .padding(14.dp)
                )
            }
        } else {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (path.isNotBlank()) {
                                path = path.substringBeforeLast('/', "")
                                scope.launch { load() }
                            }
                        },
                        enabled = path.isNotBlank() && !busy
                    ) { Text("↑  Up") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { scope.launch { load() } }, enabled = !busy) {
                        Text("↻  Refresh")
                    }
                    Text(
                        if (path.isBlank()) "Root folder" else path,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                    )
                }

                if (busy && raw.isBlank()) {
                    LoadingState("Loading shared source")
                } else if (items.length() == 0) {
                    if (raw.isBlank()) {
                        ErrorState("Shared source unavailable", "Refresh to try loading this folder again.") {
                            scope.launch { load() }
                        }
                    } else {
                        EmptyState("No shared files", "This folder has no entries.")
                    }
                } else {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val name = jstr(item, "name").ifBlank { "Unnamed entry" }
                        val itemPath = jstr(item, "path").ifBlank {
                            if (path.isBlank()) name else "$path/$name"
                        }
                        val folder = jstr(item, "type").equals("folder", ignoreCase = true) ||
                            item.optBoolean("isDirectory", false) ||
                            item.optJSONArray("children") != null

                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF4F6FA))
                                .clickable(enabled = !busy) {
                                    if (folder) {
                                        path = itemPath
                                        scope.launch { load() }
                                    } else {
                                        scope.launch {
                                            setBusy(true)
                                            try {
                                                val filePath = api.enc(itemPath)
                                                val response = JSONObject(
                                                    api.call("/projects/${api.enc(id)}/shared-source/file?path=$filePath")
                                                )
                                                content = jstr(response, "content")
                                                selected = itemPath
                                                msg("")
                                            } catch (e: Exception) {
                                                msg("Error: ${e.message ?: "Unable to read source file"}")
                                            } finally {
                                                setBusy(false)
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(36.dp).background(
                                    if (folder) Color(0xFFE4ECFC) else Color(0xFFE8EDF3),
                                    RoundedCornerShape(11.dp)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (folder) Icons.Outlined.FolderOpen else Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = Blue,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(name, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(if (folder) "FOLDER" else "SOURCE FILE", color = Muted, fontSize = 9.sp, letterSpacing = .65.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                            Box(
                                Modifier.background(Color(0xFFE6EDFA), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(if (folder) "Open" else "Read", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                        }
                        Divider(color = Line.copy(alpha = .7f), modifier = Modifier.padding(horizontal = 5.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun IssuesPage(api: Api, id: String, msg: (String) -> Unit, busy: Boolean, setBusy: (Boolean) -> Unit) {
    var raw by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        setBusy(true)
        try {
            raw = api.call("/projects/${api.enc(id)}/issues")
            msg("")
        } catch (e: Exception) {
            msg("Error: ${e.message}")
        } finally {
            setBusy(false)
        }
    }
    LaunchedEffect(id) { load() }
    val arr = try { rootArray(raw, "issues", "items", "data") } catch (_: Exception) { JSONArray() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        ScreenHeading("PROJECT ISSUES", "Issues & notes", "Keep track of things that need attention.", Icons.Outlined.BugReport)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(Color(0xFFE7EDFB), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Blue, modifier = Modifier.size(19.dp))
                }
                Text("Create issue", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
            }
            Field("Title", title, { title = it }, hint = "Issue title")
            Spacer(Modifier.height(10.dp))
            Field("Description", desc, { desc = it }, hint = "Describe the issue", singleLine = false)
            PrimaryButton("Submit issue", busy) {
                val issueTitle = title.trim()
                if (issueTitle.isBlank()) {
                    msg("Enter an issue title.")
                } else scope.launch {
                    setBusy(true)
                    try {
                        api.call(
                            "/projects/${api.enc(id)}/issues",
                            "POST",
                            JSONObject().put("title", issueTitle).put("description", desc.trim()).toString()
                        )
                        title = ""
                        desc = ""
                        load()
                        msg("Issue submitted.")
                    } catch (e: Exception) {
                        msg("Error: ${e.message}")
                    } finally {
                        setBusy(false)
                    }
                }
            }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Existing issues", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (raw.isNotBlank()) {
                    StatusPill(Blue, "${arr.length()} total")
                }
                SmallAction("Refresh") { scope.launch { load() } }
            }
            when {
                busy && raw.isBlank() -> LoadingState("Loading issues")
                raw.isBlank() -> ErrorState("Issues unavailable", "Try loading this project's issues again.") {
                    scope.launch { load() }
                }
                arr.length() == 0 -> EmptyState("Nothing to fix yet", "New project issues will show up here.")
                else -> for (i in 0 until arr.length()) {
                    val issue = arr.optJSONObject(i) ?: continue
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp)
                            .background(Color(0xFFF4F6FA), RoundedCornerShape(15.dp))
                            .border(1.dp, Line, RoundedCornerShape(15.dp))
                            .padding(13.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(Modifier.size(32.dp).background(Color(0xFFE5ECFA), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.BugReport, contentDescription = "Issue", tint = Blue, modifier = Modifier.size(17.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(jstr(issue, "title").ifBlank { "Issue" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            val description = jstr(issue, "description")
                            if (description.isNotBlank()) {
                                Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun PasswordPage(api: Api, msg: (String) -> Unit, busy: Boolean, setBusy: (Boolean) -> Unit, back: () -> Unit, parentScrolls: Boolean = false) {
    var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    val pageModifier = if (parentScrolls) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp)
    }
    Column(pageModifier) {
        ScreenHeading("ACCOUNT SECURITY", "Change password", "Update your account sign-in details.", Icons.Outlined.LockOpen)
        Panel {
            Field("Current password", old, { old = it }, secret = true)
            Spacer(Modifier.height(14.dp))
            Field("New password", new, { new = it }, secret = true)
            Spacer(Modifier.height(14.dp))
            Field("Confirm new password", confirm, { confirm = it }, secret = true)
            PrimaryButton("Update password", busy) {
                if (old.isBlank() || new.isBlank() || confirm.isBlank()) {
                    msg("Fill in all password fields.")
                } else if (new != confirm) {
                    msg("Error: New passwords do not match.")
                } else if (new == old) {
                    msg("Choose a new password different from your current password.")
                } else {
                    scope.launch {
                        setBusy(true)
                        try {
                            api.call(
                                "/auth/change-password",
                                "POST",
                                JSONObject()
                                    .put("currentPassword", old)
                                    .put("newPassword", new)
                                    .put("confirmPassword", confirm)
                                    .toString()
                            )
                            old = ""
                            new = ""
                            confirm = ""
                            msg("Password changed.")
                        } catch (e: Exception) {
                            msg("Error: ${e.message}")
                        } finally {
                            setBusy(false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = back,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(15.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
            ) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back to projects", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ScreenHeading(
    kicker: String,
    title: String,
    detail: String? = null,
    icon: ImageVector? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 18.dp).animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(Cyan, RoundedCornerShape(50)))
                Text(kicker, color = Blue, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.35.sp, modifier = Modifier.padding(start = 7.dp))
            }
            Text(title, color = Ink, fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 33.sp, modifier = Modifier.padding(top = 5.dp))
            if (!detail.isNullOrBlank()) {
                Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (icon != null) {
            Box(
                Modifier.padding(start = 12.dp).size(48.dp)
                    .background(Color(0xFFE1EAFE), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFCFDAF1), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable private fun LoginHero() {
    Box(
        Modifier.fillMaxWidth().height(211.dp)
            .background(Brush.linearGradient(listOf(Midnight, Color(0xFF17315B), Color(0xFF17647A))), RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(26.dp))
            .padding(22.dp)
    ) {
        Box(
            Modifier.size(170.dp).align(Alignment.CenterEnd).offset(x = 42.dp, y = 10.dp)
                .border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(85.dp))
        )
        Box(
            Modifier.size(112.dp).align(Alignment.CenterEnd).offset(x = (-16).dp, y = 30.dp)
                .border(1.dp, Cyan.copy(alpha = .32f), RoundedCornerShape(56.dp))
        )
        Box(
            Modifier.size(56.dp).align(Alignment.TopEnd).offset(x = (-16).dp, y = 18.dp)
                .background(Color(0xFF050C15), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painterResource(R.drawable.cs_icon), contentDescription = "CS emblem", modifier = Modifier.size(52.dp))
        }
        Column(Modifier.align(Alignment.CenterStart)) {
            Text("CS / CONTROL", color = Color(0xFFB7D5FF), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Text("Your bots,", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
            Text("within reach.", color = Color(0xFFD5F7FF), fontSize = 25.sp, fontWeight = FontWeight.Medium, lineHeight = 29.sp)
        }
    }
}

@Composable private fun ProjectHero(projectCount: Int) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).height(145.dp)
            .background(Brush.linearGradient(listOf(Midnight, Color(0xFF2458D3), Color(0xFF17859B))), RoundedCornerShape(23.dp))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(23.dp))
            .padding(18.dp)
    ) {
        Box(
            Modifier.size(124.dp).align(Alignment.CenterEnd).offset(x = 34.dp)
                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(62.dp))
        )
        Box(
            Modifier.size(73.dp).align(Alignment.CenterEnd).offset(x = (-12).dp, y = 23.dp)
                .border(1.dp, Cyan.copy(alpha = .35f), RoundedCornerShape(37.dp))
        )
        Column(Modifier.align(Alignment.CenterStart)) {
            Text("Your work,\nin one clear view.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
            Text(
                "$projectCount ${if (projectCount == 1) "project" else "projects"} · ready when you are",
                color = Color(0xFFE0EBFF),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable private fun SmallAction(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.heightIn(min = 44.dp)
            .clip(shape)
            .background(Color(0xFFEAF0FC), shape)
            .border(1.dp, Color(0xFFDCE5F7), shape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Blue, modifier = Modifier.size(15.dp))
        Text(label, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable private fun EmptyState(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 19.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(44.dp).background(Color(0xFFE9EFF8), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Inbox, contentDescription = null, tint = Blue, modifier = Modifier.size(21.dp))
        }
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 11.dp))
        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun LoadingState(label: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        repeat(3) { index ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF3F6FA)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(34.dp).background(Color(0xFFE2E9F2), RoundedCornerShape(11.dp)))
                Column(Modifier.weight(1f).padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier.fillMaxWidth(if (index == 1) .66f else .82f).height(10.dp)
                            .background(Color(0xFFE2E9F2), RoundedCornerShape(5.dp))
                    )
                    Box(
                        Modifier.fillMaxWidth(if (index == 2) .42f else .56f).height(7.dp)
                            .background(Color(0xFFE8EDF3), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable private fun ErrorState(title: String, detail: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(46.dp).background(Color(0xFFFFEFEE), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Red, modifier = Modifier.size(22.dp))
        }
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = 9.dp).heightIn(min = 44.dp),
            shape = RoundedCornerShape(13.dp)
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("  Try again", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun StatusPill(tint: Color, text: String, onDarkSurface: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .semantics { contentDescription = "Status: $text" }
            .background(if (onDarkSurface) Color.White.copy(alpha = .07f) else tint.copy(alpha = .10f), shape)
            .border(1.dp, tint.copy(alpha = .26f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(tint, RoundedCornerShape(50)))
        Text(
            text,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 7.dp)
        )
    }
}

@Composable private fun RuntimeLogRow(entry: RuntimeLogEntry) {
    val text = entry.message
    val upper = text.uppercase()
    val tone = when {
        entry.stream.contains("err", true) || listOf("ERROR", "FAILED", "BLOCKED").any { upper.contains(it) } -> Color(0xFFFF929B)
        entry.stream.contains("warn", true) || "WARN" in upper -> Color(0xFFFFCB80)
        else -> Color(0xFF72D9F0)
    }
    val channel = when {
        entry.stream.contains("err", true) -> "ERR"
        entry.stream.contains("warn", true) -> "WARN"
        else -> "OUT"
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(channel, color = tone, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.width(38.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            if (entry.timestamp.isNotBlank()) {
                Text(entry.timestamp.take(24), color = Color(0xFF91A4BE), fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(text, color = Color(0xFFE2EBF7), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp)
            .shadow(4.dp, shape)
            .background(Card, shape)
            .border(1.dp, Line, shape)
            .animateContentSize()
            .padding(19.dp),
        content = content
    )
}

@Composable private fun Field(label: String, value: String, onValue: (String) -> Unit, hint: String = "", secret: Boolean = false, singleLine: Boolean = true) {
    Column {
        Text(label, color = Ink.copy(alpha = .82f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
        val shape = RoundedCornerShape(14.dp)
        var focused by remember { mutableStateOf(false) }
        var passwordVisible by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) Color(0xFFF8FAFF) else Color(0xFFF5F7FA), shape)
                .border(if (focused) 1.5.dp else 1.dp, if (focused) Blue else Line, shape)
                .padding(start = 14.dp, end = 4.dp)
                .heightIn(min = 50.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(1f).padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, color = Muted.copy(alpha = .8f), fontSize = 13.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValue,
                    singleLine = singleLine,
                    visualTransformation = if (secret && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontFamily = CsFontFamily, fontSize = 13.sp, lineHeight = 19.sp),
                    cursorBrush = SolidColor(Blue),
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 20.dp else 94.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = label }
                )
            }
            if (secret) {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide $label" else "Show $label",
                        tint = if (passwordVisible) Blue else Muted,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
@Composable private fun PrimaryButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blue, disabledContainerColor = Blue.copy(alpha = .6f))
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
