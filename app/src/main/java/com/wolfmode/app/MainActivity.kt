package com.wolfmode.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

val PitchBlack = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF141414)
val CardSurface = Color(0xFF1C1C1C)
val CrimsonRed = Color(0xFFDC143C)
val CrimsonGlow = Color(0xFFFF1E42)

data class WolfTask(
    val id: Long,
    val title: String,
    val deadline: String,
    val isCompleted: Boolean
)

data class WolfGoal(
    val id: Long,
    val title: String,
    val targetTimestamp: Long
)

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "wolf_mode_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Wolf Mode Discipline", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WOLF MODE: DISCIPLINE OVER MOOD")
            .setContentText("Check your daily targets. Do not break your streak!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(101, notification)
        return Result.success()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkColors = darkColorScheme(
                primary = CrimsonRed,
                onPrimary = Color.White,
                background = PitchBlack,
                surface = DarkSurface,
                onSurface = Color.White
            )
            MaterialTheme(colorScheme = darkColors) {
                WolfModeApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WolfModeApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wolf_mode_prefs", Context.MODE_PRIVATE) }

    // Offline Storage State
    var savedPin by remember { mutableStateOf(prefs.getString("app_pin", "") ?: "") }
    var isUnlocked by remember { mutableStateOf(savedPin.isEmpty()) }
    var currentStreak by remember { mutableIntStateOf(prefs.getInt("current_streak", 1)) }
    var longestStreak by remember { mutableIntStateOf(prefs.getInt("longest_streak", 1)) }

    val tasks = remember {
        mutableStateListOf<WolfTask>().apply {
            val raw = prefs.getString("tasks_json", null)
            if (raw != null) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(WolfTask(obj.getLong("id"), obj.getString("title"), obj.getString("deadline"), obj.getBoolean("isCompleted")))
                }
            } else {
                add(WolfTask(1L, "Morning Workout (No Excuses)", "07:00 AM", false))
                add(WolfTask(2L, "Deep Focus Work (2 Hours)", "11:00 AM", false))
                add(WolfTask(3L, "Zero Junk Food Today", "09:00 PM", false))
            }
        }
    }

    val goals = remember {
        mutableStateListOf<WolfGoal>().apply {
            val raw = prefs.getString("goals_json", null)
            if (raw != null) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(WolfGoal(obj.getLong("id"), obj.getString("title"), obj.getLong("targetTimestamp")))
                }
            } else {
                add(WolfGoal(1L, "90-Day Iron Discipline Transformation", System.currentTimeMillis() + TimeUnit.DAYS.toMillis(45)))
            }
        }
    }

    fun saveTasks() {
        val arr = JSONArray()
        tasks.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            obj.put("deadline", it.deadline)
            obj.put("isCompleted", it.isCompleted)
            arr.put(obj)
        }
        prefs.edit().putString("tasks_json", arr.toString()).apply()
    }

    fun saveGoals() {
        val arr = JSONArray()
        goals.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            obj.put("targetTimestamp", it.targetTimestamp)
            arr.put(obj)
        }
        prefs.edit().putString("goals_json", arr.toString()).apply()
    }

    // Notification Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "WolfDailyReminder",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (!isUnlocked) {
        PinLockScreen(expectedPin = savedPin, onUnlock = { isUnlocked = true })
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val completedCount = tasks.count { it.isCompleted }
    val completionPercent = if (tasks.isEmpty()) 0 else (completedCount * 100) / tasks.size
    val disciplineScore = ((completionPercent * 0.7) + (minOf(currentStreak, 30))).toInt().coerceIn(0, 100)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .shadow(8.dp, CircleShape, spotColor = CrimsonGlow)
                                .background(CrimsonRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "Wolf Logo", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("WOLF MODE", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = Color.White, fontSize = 18.sp)
                            Text("DISCIPLINE OVER MOOD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CrimsonRed, letterSpacing = 1.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = DarkSurface) {
                val items = listOf(
                    Triple("Dashboard", Icons.Default.Dashboard, 0),
                    Triple("Tasks", Icons.Default.CheckCircle, 1),
                    Triple("Countdown", Icons.Default.Timer, 2),
                    Triple("Streak", Icons.Default.LocalFireDepartment, 3)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CrimsonRed,
                            indicatorColor = CrimsonRed,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PitchBlack)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(disciplineScore, completionPercent, currentStreak, tasks)
                1 -> TasksTab(
                    tasks = tasks,
                    onTasksChanged = {
                        saveTasks()
                        if (tasks.isNotEmpty() && tasks.all { it.isCompleted }) {
                            currentStreak += 1
                            if (currentStreak > longestStreak) longestStreak = currentStreak
                            prefs.edit()
                                .putInt("current_streak", currentStreak)
                                .putInt("longest_streak", longestStreak)
                                .apply()
                        }
                    }
                )
                2 -> CountdownTab(goals = goals, onGoalsChanged = { saveGoals() })
                3 -> StreakAndSettingsTab(
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    savedPin = savedPin,
                    onPinUpdated = { newPin ->
                        savedPin = newPin
                        prefs.edit().putString("app_pin", newPin).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun PinLockScreen(expectedPin: String, onUnlock: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(PitchBlack).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("WOLF MODE LOCKED", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                error = false
                if (it == expectedPin) onUnlock()
            },
            label = { Text("Enter 4-Digit PIN") },
            singleLine = true
        )
        if (error) {
            Text("Incorrect PIN", color = CrimsonRed, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { if (input == expectedPin) onUnlock() else error = true },
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
        ) {
            Text("UNLOCK")
        }
    }
}

@Composable
fun DashboardTab(score: Int, completionPercent: Int, streak: Int, tasks: List<WolfTask>) {
    val quotes = listOf(
        "Discipline is choosing between what you want now and what you want most.",
        "Nobody cares about your excuses. Execute the mission.",
        "Pain is temporary. Regret lasts forever.",
        "While they sleep, the wolf hunts."
    )
    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val quote = quotes[dayOfYear % quotes.size]
    val missedCount = tasks.count { !it.isCompleted }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("DISCIPLINE SCORE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$score", fontSize = 48.sp, fontWeight = FontWeight.Black, color = CrimsonRed)
                        Text(" / 100", fontSize = 18.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { completionPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = CrimsonRed,
                        trackColor = Color(0xFF2B2B2B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Daily Completion: $completionPercent%  •  Active Streak: $streak Days", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }

        if (missedCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A080D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonGlow)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("$missedCount pending tasks remaining today. Finish strong!", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("DAILY WOLF MANTRA", color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(""$quote"", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun TasksTab(tasks: MutableList<WolfTask>, onTasksChanged: () -> Unit) {
    var titleInput by remember { mutableStateOf("") }
    var deadlineInput by remember { mutableStateOf("08:00 PM") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("New Mission / Task") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = deadlineInput,
                        onValueChange = { deadlineInput = it },
                        label = { Text("Deadline") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (titleInput.isNotBlank()) {
                                tasks.add(WolfTask(System.currentTimeMillis(), titleInput.trim(), deadlineInput.trim(), false))
                                titleInput = ""
                                onTasksChanged()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Text("ADD")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { checked ->
                                val idx = tasks.indexOfFirst { it.id == task.id }
                                if (idx != -1) {
                                    tasks[idx] = task.copy(isCompleted = checked)
                                    onTasksChanged()
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = CrimsonRed)
                        )
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                text = task.title,
                                color = if (task.isCompleted) Color.Gray else Color.White,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Target: ${task.deadline}", color = CrimsonRed, fontSize = 11.sp)
                        }
                        IconButton(onClick = {
                            tasks.removeAll { it.id == task.id }
                            onTasksChanged()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CountdownTab(goals: MutableList<WolfGoal>, onGoalsChanged: () -> Unit) {
    var goalName by remember { mutableStateOf("") }
    var daysInput by remember { mutableStateOf("30") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = goalName,
                    onValueChange = { goalName = it },
                    label = { Text("Goal Title (e.g., Exam / Fitness)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = daysInput,
                        onValueChange = { daysInput = it },
                        label = { Text("Days From Now") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val d = daysInput.toLongOrNull() ?: 30L
                            if (goalName.isNotBlank() && d > 0) {
                                goals.add(WolfGoal(System.currentTimeMillis(), goalName.trim(), System.currentTimeMillis() + TimeUnit.DAYS.toMillis(d)))
                                goalName = ""
                                onGoalsChanged()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("SET GOAL")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(goals, key = { it.id }) { goal ->
                val diff = (goal.targetTimestamp - now).coerceAtLeast(0L)
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CrimsonRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(goal.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = {
                                goals.removeAll { it.id == goal.id }
                                onGoalsChanged()
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            TimeBox("$days", "DAYS")
                            TimeBox("$hours", "HOURS")
                            TimeBox("$minutes", "MINS")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeBox(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(PitchBlack, RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(value, color = CrimsonRed, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StreakAndSettingsTab(
    currentStreak: Int,
    longestStreak: Int,
    savedPin: String,
    onPinUpdated: (String) -> Unit
) {
    var pinInput by remember { mutableStateOf(savedPin) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(32.dp))
                    Text("$currentStreak DAYS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("CURRENT STREAK", color = Color.Gray, fontSize = 10.sp)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(32.dp))
                    Text("$longestStreak DAYS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("LONGEST STREAK", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SECURITY PIN LOCK (OPTIONAL)", color = CrimsonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4) pinInput = it },
                    label = { Text("4-Digit PIN (Leave empty to disable)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onPinUpdated(pinInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE SECURITY SETTINGS")
                }
            }
        }
    }
}
