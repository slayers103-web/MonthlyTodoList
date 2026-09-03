package com.example.monthlytodolist

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationScheduler.scheduleMonthlyAlarms(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { MonthlyTodoScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyTodoScreen() {
    val context = LocalContext.current
    val repository = remember { TodoRepository(context) }
    var currentMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var todos by remember { mutableStateOf(repository.getTodos()) }
    var refresh by remember { mutableStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var showNotificationHelp by remember { mutableStateOf(false) }

    val month = YearMonth.parse(currentMonth)
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy년 MM월", Locale.KOREAN) }

    fun reload() {
        todos = repository.getTodos()
        refresh++
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(repository.buildBackup().toByteArray(Charsets.UTF_8))
                } ?: error("파일을 열 수 없습니다.")
                Toast.makeText(context, "백업이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "백업 실패: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("파일을 읽을 수 없습니다.")
                repository.restoreBackup(json).getOrThrow()
                reload()
                Toast.makeText(context, "복원이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "복원 실패: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(context, if (granted) "알림 권한이 허용되었습니다." else "알림 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(refresh) {
        NotificationScheduler.scheduleMonthlyAlarms(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("매월 할 일") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "메뉴")
                    }
                    DropdownMenu(menuExpanded, { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("데이터 백업") },
                            leadingIcon = { Icon(Icons.Default.Backup, null) },
                            onClick = {
                                menuExpanded = false
                                backupLauncher.launch("monthly-todo-backup.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("데이터 복원") },
                            leadingIcon = { Icon(Icons.Default.Backup, null) },
                            onClick = {
                                menuExpanded = false
                                restoreLauncher.launch(arrayOf("application/json", "text/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("알림 권한 설정") },
                            leadingIcon = { Icon(Icons.Default.Notifications, null) },
                            onClick = {
                                menuExpanded = false
                                if (Build.VERSION.SDK_INT >= 33) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else showNotificationHelp = true
                                } else showNotificationHelp = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = month.minusMonths(1).toString() }) {
                    Icon(Icons.Default.ArrowBack, "이전 달")
                }
                Text(month.format(formatter), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { currentMonth = month.plusMonths(1).toString() }) {
                    Icon(Icons.Default.ArrowForward, "다음 달")
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("매월 반복할 할 일") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            repository.addTodo(input)
                            input = ""
                            reload()
                        }
                    }
                ) { Icon(Icons.Default.Add, null); Text("추가") }
            }

            Spacer(Modifier.height(12.dp))
            val completed = todos.count { repository.isDone(month, it.id) }
            Text("${completed} / ${todos.size} 완료", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                items(todos, key = { it.id }) { todo ->
                    val done = repository.isDone(month, todo.id)
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = done,
                                onCheckedChange = { checked ->
                                    repository.setDone(month, todo.id, checked)
                                    refresh++
                                }
                            )
                            Text(
                                todo.text,
                                Modifier.weight(1f).padding(horizontal = 8.dp),
                                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                            )
                            IconButton(onClick = { editingTodo = todo }) { Icon(Icons.Default.Edit, "수정") }
                            IconButton(onClick = { repository.deleteTodo(todo.id); reload() }) {
                                Icon(Icons.Default.Delete, "삭제")
                            }
                        }
                    }
                }
            }
        }
    }

    editingTodo?.let { todo ->
        EditTodoDialog(
            initial = todo.text,
            onDismiss = { editingTodo = null },
            onSave = { newText ->
                repository.updateTodo(todo.id, newText)
                editingTodo = null
                reload()
            }
        )
    }

    if (showNotificationHelp) {
        AlertDialog(
            onDismissRequest = { showNotificationHelp = false },
            title = { Text("알림 설정") },
            text = { Text("매월 말일 기준 7일 전, 3일 전, 1일 전에 미완료 항목이 있으면 알림을 보냅니다. Android 12 이상에서는 정확한 알람 권한을 허용하면 알림 시각이 더 정확해집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationHelp = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }) { Text("알람 권한 열기") }
            },
            dismissButton = { TextButton(onClick = { showNotificationHelp = false }) { Text("닫기") } }
        )
    }
}

@Composable
private fun EditTodoDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("할 일 수정") },
        text = { OutlinedTextField(text, { text = it }, label = { Text("할 일") }, singleLine = true) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
