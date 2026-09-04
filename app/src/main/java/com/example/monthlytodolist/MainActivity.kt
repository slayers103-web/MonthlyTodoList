package com.example.monthlytodolist

import android.Manifest
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TodoRepository(this).prepareMonths()
        NotificationScheduler.scheduleMonthlyAlarms(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    MonthlyTodoScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyTodoScreen() {
    val context = LocalContext.current
    val repository = remember { TodoRepository(context) }
    var currentMonthText by remember { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(currentMonthText)
    val today = YearMonth.now()
    var historicalUnlocked by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    val editable = month >= today || (month < today && historicalUnlocked)
    var todos by remember { mutableStateOf(repository.getMonthItems(month)) }
    var refresh by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var deletingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showNotificationHelp by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
    }

    fun reload() {
        repository.prepareMonths()
        todos = repository.getMonthItems(month)
        refresh++
    }

    LaunchedEffect(month) {
        // Historical-month unlock is session-only. Changing month starts locked.
        historicalUnlocked = false
    }

    LaunchedEffect(month, refresh) {
        repository.prepareMonths()
        todos = repository.getMonthItems(month)
        NotificationScheduler.scheduleMonthlyAlarms(context)
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(repository.buildBackup().toByteArray(Charsets.UTF_8))
                } ?: error("파일을 열 수 없습니다.")
                Toast.makeText(context, "백업이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "백업 실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("파일을 읽을 수 없습니다.")
                repository.restoreBackup(json).getOrThrow()
                currentMonthText = YearMonth.now().toString()
                reload()
                Toast.makeText(context, "복원이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "복원 실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "알림 권한이 허용되었습니다." else "알림 권한이 거부되었습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly To-Do-List") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF00897B),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    // Custom vector drawables are used so the two directions are
                    // visually unmistakable and do not depend on icon-library names.
                    IconButton(onClick = { backupLauncher.launch("mtdl_backup.json") }) {
                        Icon(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_backup_download),
                            contentDescription = "데이터 백업"
                        )
                    }
                    IconButton(onClick = {
                        restoreLauncher.launch(arrayOf("application/json", "text/*"))
                    }) {
                        Icon(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_restore_upload),
                            contentDescription = "데이터 복원"
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("알림 권한 설정") },
                                leadingIcon = { Icon(Icons.Default.Notifications, null) },
                                onClick = {
                                    menuExpanded = false
                                    if (Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    } else {
                                        showNotificationHelp = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("앱 정보") },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = {
                                    menuExpanded = false
                                    showInfo = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(month) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            when {
                                totalDrag < -80f -> currentMonthText = month.plusMonths(1).toString()
                                totalDrag > 80f -> currentMonthText = month.minusMonths(1).toString()
                            }
                        }
                    )
                }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    currentMonthText = month.minusMonths(1).toString()
                }) {
                    Icon(Icons.Default.ArrowBack, "이전 달")
                }
                Text(month.format(formatter), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = {
                    currentMonthText = month.plusMonths(1).toString()
                }) {
                    Icon(Icons.Default.ArrowForward, "다음 달")
                }
            }

            Text(
                "매월 체크 항목",
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val completed = todos.count { repository.isDone(month, it.id) }
                Text(
                    "[$completed / ${todos.size} 완료]",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (editable && todos.isEmpty()) {
                Text(
                    "오른쪽 아래 + 버튼으로 체크 항목을 추가할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (todos.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        if (editable) "아직 체크 항목이 없습니다.\n오른쪽 아래 + 버튼으로 항목을 추가해 보세요."
                        else "이 달에는 저장된 체크 항목이 없습니다.",
                        Modifier.padding(18.dp)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(todos, key = { it.id }) { todo ->
                        val done = repository.isDone(month, todo.id)
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = done,
                                    enabled = editable,
                                    onCheckedChange = { checked ->
                                        repository.setDone(month, todo.id, checked, allowHistoricalEdit = historicalUnlocked)
                                        reload()
                                    }
                                )
                                Text(
                                    todo.text,
                                    Modifier
                                        .weight(1f)
                                        .clickable(enabled = editable) {
                                            repository.setDone(
                                                month,
                                                todo.id,
                                                !done,
                                                allowHistoricalEdit = historicalUnlocked
                                            )
                                            reload()
                                        }
                                        .padding(horizontal = 6.dp),
                                    textDecoration = if (done) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                    color = if (editable) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (editable) {
                                    IconButton(onClick = { editingTodo = todo }) {
                                        Icon(Icons.Default.Edit, "수정")
                                    }
                                    IconButton(onClick = { deletingTodo = todo }) {
                                        Icon(Icons.Default.Delete, "삭제")
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.Lock,
                                        "과거 데이터",
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    month < today && historicalUnlocked ->
                        "잠금이 해제된 과거 월입니다. 앱을 다시 실행하면 자동으로 잠깁니다."
                    month < today ->
                        "지난 달은 기본 잠금 상태입니다. 왼쪽 아래 자물쇠 버튼으로 잠시 잠금을 해제할 수 있습니다."
                    else ->
                        "이번 달에 추가·수정한 항목은 다음 달에도 이어집니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        if (month < today) {
            FloatingActionButton(
                onClick = {
                    if (historicalUnlocked) {
                        historicalUnlocked = false
                    } else {
                        showUnlockDialog = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    if (historicalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = if (historicalUnlocked) "다시 잠그기" else "잠금 해제"
                )
            }
        }

        if (editable && month >= today) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "체크 항목 추가")
            }
        }
        }
    }

    editingTodo?.let { todo ->
        EditTodoDialog(
            initial = todo.text,
            onDismiss = { editingTodo = null },
            onSave = { newText ->
                repository.updateTodo(month, todo.id, newText, allowHistoricalEdit = historicalUnlocked)
                editingTodo = null
                reload()
            }
        )
    }

    if (showAddDialog) {
        AddTodoDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { text ->
                repository.addTodo(month, text)
                showAddDialog = false
                reload()
            }
        )
    }

    deletingTodo?.let { todo ->
        AlertDialog(
            onDismissRequest = { deletingTodo = null },
            title = { Text("체크 항목 삭제") },
            text = { Text("‘${todo.text}’ 항목을 삭제하시겠습니까?\n삭제하면 현재 달의 목록에서 제거됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteTodo(month, todo.id, allowHistoricalEdit = historicalUnlocked)
                    deletingTodo = null
                    reload()
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTodo = null }) { Text("취소") }
            }
        )
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text("과거 월 잠금 해제") },
            text = {
                Text(
                    "지난 달의 데이터를 잠시 수정할 수 있도록 잠금을 해제하시겠습니까?\n\n" +
                        "잠금 해제 상태는 앱을 종료하거나 다시 실행하면 자동으로 초기화됩니다."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    historicalUnlocked = true
                    showUnlockDialog = false
                }) { Text("잠금 해제") }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) { Text("취소") }
            }
        )
    }

    if (showNotificationHelp) {
        AlertDialog(
            onDismissRequest = { showNotificationHelp = false },
            title = { Text("알림 설정") },
            text = {
                Text(
                    "매월 말일 기준 7일 전, 3일 전, 1일 전에 미완료 항목이 있으면 알림을 보냅니다.\n\n" +
                        "Android 12 이상에서는 정확한 알람 권한을 허용하면 알림 시각이 더 정확해집니다."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationHelp = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }) { Text("알람 권한 열기") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationHelp = false }) { Text("닫기") }
            }
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("MTDL") },
            text = {
                Text(
                    "Monthly To-Do-List\n\n" +
                        "매월 체크 항목을 관리하고 완료 상태를 기록합니다.\n\n" +
                        "• 월별 데이터 자동 이어받기\n" +
                        "• 지난 달 기록 보존\n" +
                        "• 데이터 백업 및 복원"
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("확인") }
            }
        )
    }
}

@Composable
private fun AddTodoDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("체크 항목 추가") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("항목명") },
                placeholder = { Text("체크할 항목을 입력하세요") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onAdd(text.trim()) }
            ) { Text("추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun EditTodoDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("체크 항목 수정") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("항목명") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSave(text.trim()) }
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
