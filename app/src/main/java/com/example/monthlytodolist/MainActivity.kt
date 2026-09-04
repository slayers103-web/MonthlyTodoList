package com.example.monthlytodolist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TodoRepository(this).prepareMonths()
        NotificationScheduler.scheduleMonthlyAlarms(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { MonthlyTodoScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
    var fontMenuExpanded by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(repository.getFontSize()) }
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var deletingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var numberTodo by remember { mutableStateOf<Pair<TodoItem, Int>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showNotificationHelp by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf<String?>(null) }
    var monthDirection by remember { mutableStateOf(1) }

    val formatter = remember { DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN) }

    fun reload() {
        repository.prepareMonths()
        todos = repository.getMonthItems(month)
        refresh++
    }

    fun moveMonth(delta: Long) {
        monthDirection = if (delta > 0) 1 else -1
        currentMonthText = month.plusMonths(delta).toString()
    }

    LaunchedEffect(month) { historicalUnlocked = false }
    LaunchedEffect(month, refresh) {
        repository.prepareMonths()
        todos = repository.getMonthItems(month)
        NotificationScheduler.scheduleMonthlyAlarms(context)
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(repository.buildBackup().toByteArray(Charsets.UTF_8)) }
                ?: error("파일을 열 수 없습니다.")
            Toast.makeText(context, "백업이 저장되었습니다.", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "백업 실패: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("파일을 읽을 수 없습니다.")
            repository.restoreBackup(json).getOrThrow()
            currentMonthText = YearMonth.now().toString()
            reload()
            Toast.makeText(context, "복원이 완료되었습니다.", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "복원 실패: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(context, if (granted) "알림 권한이 허용되었습니다." else "알림 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly To-Do-List") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF00897B), titleContentColor = Color.White, actionIconContentColor = Color.White
                ),
                actions = {
                    if (editable) {
                        IconButton(onClick = { selectionMode = if (selectionMode == "edit") null else "edit" }) {
                            Icon(Icons.Default.Edit, if (selectionMode == "edit") "수정 선택 취소" else "항목 수정")
                        }
                        IconButton(onClick = { selectionMode = if (selectionMode == "delete") null else "delete" }) {
                            Icon(Icons.Default.Delete, if (selectionMode == "delete") "삭제 선택 취소" else "항목 삭제")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "메뉴") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("항목 글씨 크기") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menuExpanded = false; fontMenuExpanded = true }
                            )
                            DropdownMenuItem(
                                text = { Text("데이터 백업") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_backup_download), null) },
                                onClick = { menuExpanded = false; backupLauncher.launch("mtdl_backup_v3.json") }
                            )
                            DropdownMenuItem(
                                text = { Text("데이터 복원") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_restore_upload), null) },
                                onClick = { menuExpanded = false; restoreLauncher.launch(arrayOf("application/json", "text/*")) }
                            )
                            DropdownMenuItem(
                                text = { Text("알림 권한 설정") },
                                leadingIcon = { Icon(Icons.Default.Notifications, null) },
                                onClick = {
                                    menuExpanded = false
                                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else showNotificationHelp = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("앱 정보") }, leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = { menuExpanded = false; showInfo = true }
                            )
                        }
                        DropdownMenu(expanded = fontMenuExpanded, onDismissRequest = { fontMenuExpanded = false }) {
                            Column(Modifier.width(250.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text("항목 글씨 크기", fontWeight = FontWeight.SemiBold)
                                Text("${fontSize.toInt()}sp", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = fontSize,
                                    onValueChange = { fontSize = it; repository.setFontSize(it) },
                                    valueRange = 10f..30f,
                                    steps = 19
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("10sp", style = MaterialTheme.typography.labelSmall)
                                    Text("30sp", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).pointerInput(month) {
                var totalX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalX = 0f },
                    onHorizontalDrag = { change, dragAmount -> totalX += dragAmount; change.consume() },
                    onDragEnd = { if (abs(totalX) > 90f) { if (totalX < 0f) moveMonth(1) else moveMonth(-1) } },
                    onDragCancel = { totalX = 0f }
                )
            }
        ) {
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val sign = monthDirection
                    (slideInHorizontally { fullWidth -> sign * fullWidth } + fadeIn()) togetherWith
                        (slideOutHorizontally { fullWidth -> -sign * fullWidth } + fadeOut())
                }, label = "month transition"
            ) { animatedMonth ->
                val animatedTodos = if (animatedMonth == month) todos else repository.getMonthItems(animatedMonth)
                MonthContent(
                    month = animatedMonth,
                    today = today,
                    todos = animatedTodos,
                    editable = animatedMonth >= today || (animatedMonth < today && historicalUnlocked),
                    historicalUnlocked = historicalUnlocked,
                    formatter = formatter,
                    repository = repository,
                    fontSize = fontSize,
                    onPrevious = { moveMonth(-1) },
                    onNext = { moveMonth(1) },
                    onToggle = { todo, done -> repository.setDone(animatedMonth, todo.id, done, historicalUnlocked); reload() },
                    onNumber = { todo, slot -> if (selectionMode == null) numberTodo = todo to slot },
                    onEdit = { todo -> editingTodo = todo; selectionMode = null },
                    onDelete = { todo -> deletingTodo = todo; selectionMode = null },
                    selectionMode = selectionMode,
                    onSelect = { todo ->
                        when (selectionMode) {
                            "edit" -> { editingTodo = todo; selectionMode = null }
                            "delete" -> { deletingTodo = todo; selectionMode = null }
                        }
                    },
                    onReorder = { todo, sectionDone, targetIndex -> repository.reorderTodo(animatedMonth, todo.id, targetIndex, sectionDone, historicalUnlocked); reload() }
                )
            }

            if (month < today) {
                FloatingActionButton(
                    onClick = { if (historicalUnlocked) historicalUnlocked = false else showUnlockDialog = true },
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                    containerColor = Color(0xFF7E57C2),
                    contentColor = Color.White
                ) { Icon(if (historicalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock, if (historicalUnlocked) "다시 잠그기" else "잠금 해제") }
            }
            if (editable && month >= today) {
                FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Color(0xFF7E57C2), contentColor = Color.White) {
                    Icon(Icons.Default.Add, "체크 항목 추가")
                }
            }
        }
    }

    editingTodo?.let { todo ->
        EditTodoDialog(
            initial = todo.text,
            initialPriority = todo.priority,
            initialNumber1 = todo.number1,
            onDismiss = { editingTodo = null },
            onSave = { newText, priority, number1 ->
                repository.updateTodo(month, todo.id, newText, priority, number1, historicalUnlocked)
                editingTodo = null
                reload()
            }
        )
    }
    if (showAddDialog) {
        AddTodoDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { text, priority, number1 -> repository.addTodo(month, text, priority, number1); showAddDialog = false; reload() }
        )
    }
    numberTodo?.let { (todo, slot) ->
        NumberInputDialog(
            title = if (slot == 1) "필요 숫자 기록" else "완료 숫자 기록",
            initial = if (slot == 1) todo.number1 else todo.number2,
            onDismiss = { numberTodo = null },
            onSave = { number -> repository.updateNumber(month, todo.id, slot, number, historicalUnlocked); numberTodo = null; reload() }
        )
    }
    deletingTodo?.let { todo ->
        AlertDialog(
            onDismissRequest = { deletingTodo = null }, title = { Text("체크 항목 삭제") },
            text = { Text("‘${todo.text}’ 항목을 삭제하시겠습니까?\n삭제하면 현재 달의 목록에서 제거됩니다.") },
            confirmButton = { TextButton(onClick = { repository.deleteTodo(month, todo.id, historicalUnlocked); deletingTodo = null; reload() }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deletingTodo = null }) { Text("취소") } }
        )
    }
    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false }, title = { Text("과거 월 잠금 해제") },
            text = { Text("지난 달의 데이터를 잠시 수정할 수 있도록 잠금을 해제하시겠습니까?\n\n잠금 해제 상태는 앱을 종료하거나 다시 실행하면 자동으로 초기화됩니다.") },
            confirmButton = { TextButton(onClick = { historicalUnlocked = true; showUnlockDialog = false }) { Text("잠금 해제") } },
            dismissButton = { TextButton(onClick = { showUnlockDialog = false }) { Text("취소") } }
        )
    }
    if (showNotificationHelp) {
        AlertDialog(
            onDismissRequest = { showNotificationHelp = false }, title = { Text("알림 설정") },
            text = { Text("매월 말일 기준 7일 전, 3일 전, 1일 전에 미완료 항목이 있으면 알림을 보냅니다.\n\nAndroid 12 이상에서는 정확한 알람 권한을 허용하면 알림 시각이 더 정확해집니다.") },
            confirmButton = { TextButton(onClick = { showNotificationHelp = false; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text("알람 권한 열기") } },
            dismissButton = { TextButton(onClick = { showNotificationHelp = false }) { Text("닫기") } }
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false }, title = { Text("MTDL") },
            text = {
                Text("Monthly To-Do-List\n\n매월 체크 항목을 관리하고 완료 상태를 기록합니다.\n\n• 월별 데이터 자동 이어받기\n• 완료/미완료 구역 및 체크 순서 정렬\n• 우선순위 지정 및 우선순위별 정렬\n• 항목 글씨 크기 조절\n• 항목별 필요/완료 숫자 기록 및 비교\n• 롱프레스 드래그 정렬\n• 지난 달 기록 보존 및 잠시 잠금 해제\n• 데이터 백업 및 복원\n• 월말 미완료 알림")
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("확인") } }
        )
    }
}

private fun TodoItem.displayText(): String = buildString {
    if (priority != null) append("[$priority] ")
    append(text)
    if (number1 != null) append("   필요 $number1")
    if (number2 != null) append("   완료 $number2")
}

@Composable
private fun MonthContent(
    month: YearMonth,
    today: YearMonth,
    todos: List<TodoItem>,
    editable: Boolean,
    historicalUnlocked: Boolean,
    formatter: DateTimeFormatter,
    repository: TodoRepository,
    fontSize: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: (TodoItem, Boolean) -> Unit,
    onNumber: (TodoItem, Int) -> Unit,
    onEdit: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit,
    selectionMode: String?,
    onSelect: (TodoItem) -> Unit,
    onReorder: (TodoItem, Boolean, Int) -> Unit
) {
    val pending = todos.filter { !repository.isDone(month, it.id) }
    val completed = todos.filter { repository.isDone(month, it.id) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "이전 달") }
            Text(month.format(formatter), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "다음 달") }
        }
        Text("매월 체크 항목", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.End) {
            Text("[${completed.size} / ${todos.size} 완료]", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val scale = (fontSize / 16f).coerceIn(0.625f, 1.875f)
        Row(
            Modifier.fillMaxWidth()
                .padding(bottom = (4f * scale).dp)
                .height((34f * scale).dp)
                .clip(RoundedCornerShape((10f * scale).dp))
                .background(Color(0xFF7E57C2))
                .border((1f * scale).dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape((10f * scale).dp))
        ) {
            HeaderCell("항목", Modifier.weight(1f), fontSize, scale, dividerEnd = true)
            HeaderCell("필요", Modifier.width((68f * scale).dp), fontSize, scale, dividerEnd = true)
            HeaderCell("완료", Modifier.width((68f * scale).dp), fontSize, scale, dividerEnd = false)
        }

        if (todos.isEmpty()) {
            if (editable) Text("오른쪽 아래 + 버튼으로 체크 항목을 추가할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(if (editable) "아직 체크 항목이 없습니다.\n오른쪽 아래 + 버튼으로 항목을 추가해 보세요." else "이 달에는 저장된 체크 항목이 없습니다.", Modifier.padding(18.dp))
            }
        } else {
            val listState = rememberLazyListState()
            var draggedId by remember { mutableStateOf<String?>(null) }
            var draggedSectionDone by remember { mutableStateOf(false) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            var dropTargetIndex by remember { mutableStateOf<Int?>(null) }
            var dragCenters by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
            var dragBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }

            fun updateDropTarget(section: List<TodoItem>, todo: TodoItem) {
                val candidates = section.filter { it.id != todo.id }
                val center = dragBounds[todo.id]?.center?.y?.plus(dragOffset) ?: return
                val index = candidates.count { (dragCenters[it.id] ?: Float.POSITIVE_INFINITY) < center }
                dropTargetIndex = index.coerceIn(0, candidates.size)
            }

            val density = LocalDensity.current
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (pending.isNotEmpty()) {
                        item(key = "pending_header") { SectionHeader("미완료", pending.size) }
                        items(pending, key = { it.id }) { todo ->
                            ReorderableTodoRow(
                                todo = todo, done = false, editable = editable, isDragging = draggedId == todo.id, dragOffset = dragOffset, fontSize = fontSize,
                                onToggle = { onToggle(todo, it) }, onNumber = { slot -> onNumber(todo, slot) },
                                onEdit = { onEdit(todo) }, onDelete = { onDelete(todo) },
                                selectionMode = selectionMode, onSelect = { onSelect(todo) },
                                onDragStart = {
                                    draggedId = todo.id; draggedSectionDone = false; dragOffset = 0f
                                    val snapshot = pending.filter { it.id != todo.id }
                                    dragCenters = snapshot.associate { item -> item.id to (dragBounds[item.id]?.center?.y ?: 0f) }
                                    dropTargetIndex = pending.indexOf(todo)
                                },
                                onDrag = { amount -> dragOffset += amount; updateDropTarget(pending, todo) },
                                onDragEnd = { onReorder(todo, false, dropTargetIndex ?: pending.indexOf(todo)); draggedId = null; dragOffset = 0f; dropTargetIndex = null; dragCenters = emptyMap(); dragBounds = emptyMap() },
                                onDragCancel = { draggedId = null; dragOffset = 0f; dropTargetIndex = null; dragCenters = emptyMap(); dragBounds = emptyMap() },
                                onBoundsChanged = { rect -> dragBounds = dragBounds + (todo.id to rect) }
                            )
                        }
                    }
                    if (completed.isNotEmpty()) {
                        item(key = "completed_header") { SectionHeader("완료", completed.size) }
                        items(completed, key = { it.id }) { todo ->
                            ReorderableTodoRow(
                                todo = todo, done = true, editable = editable, isDragging = draggedId == todo.id, dragOffset = dragOffset, fontSize = fontSize,
                                onToggle = { onToggle(todo, it) }, onNumber = { slot -> onNumber(todo, slot) },
                                onEdit = { onEdit(todo) }, onDelete = { onDelete(todo) },
                                selectionMode = selectionMode, onSelect = { onSelect(todo) },
                                onDragStart = {
                                    draggedId = todo.id; draggedSectionDone = true; dragOffset = 0f
                                    val snapshot = completed.filter { it.id != todo.id }
                                    dragCenters = snapshot.associate { item -> item.id to (dragBounds[item.id]?.center?.y ?: 0f) }
                                    dropTargetIndex = completed.indexOf(todo)
                                },
                                onDrag = { amount -> dragOffset += amount; updateDropTarget(completed, todo) },
                                onDragEnd = { onReorder(todo, true, dropTargetIndex ?: completed.indexOf(todo)); draggedId = null; dragOffset = 0f; dropTargetIndex = null; dragCenters = emptyMap(); dragBounds = emptyMap() },
                                onDragCancel = { draggedId = null; dragOffset = 0f; dropTargetIndex = null; dragCenters = emptyMap(); dragBounds = emptyMap() },
                                onBoundsChanged = { rect -> dragBounds = dragBounds + (todo.id to rect) }
                            )
                        }
                    }
                }
                if (draggedId != null) {
                    val target = dropTargetIndex
                    val section = if (draggedSectionDone) completed else pending
                    val candidates = section.filter { it.id != draggedId }
                    if (target != null) {
                        val y = when {
                            candidates.isEmpty() -> dragBounds[draggedId]?.top ?: 0f
                            target <= 0 -> dragBounds[candidates.first().id]?.top ?: 0f
                            target >= candidates.size -> dragBounds[candidates.last().id]?.bottom ?: 0f
                            else -> dragBounds[candidates[target].id]?.top ?: 0f
                        }
                        Box(Modifier.fillMaxWidth().offset(y = with(density) { (y - 2f).toDp() })) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
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
                month < today && historicalUnlocked -> "잠금이 해제된 과거 월입니다. 앱을 다시 실행하면 자동으로 잠깁니다."
                month < today -> "지난 달은 기본 잠금 상태입니다. 왼쪽 아래 자물쇠 버튼으로 잠시 잠금을 해제할 수 있습니다."
                else -> "이번 달에 추가·수정한 항목은 다음 달에도 이어집니다."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun HeaderCell(
    title: String,
    modifier: Modifier,
    fontSize: Float,
    scale: Float,
    dividerEnd: Boolean
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White, textAlign = TextAlign.Center
        )
        if (dividerEnd) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width((1f * scale).dp)
                    .background(Color.White.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text("  $count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReorderableTodoRow(
    todo: TodoItem, done: Boolean, editable: Boolean, isDragging: Boolean, dragOffset: Float, fontSize: Float,
    onToggle: (Boolean) -> Unit, onNumber: (Int) -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit,
    selectionMode: String?, onSelect: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
    onBoundsChanged: (Rect) -> Unit
) {
    val scale = (fontSize / 16f).coerceIn(0.625f, 1.875f)
    val numbersMatch = todo.number1 != null && todo.number2 != null && todo.number1 == todo.number2
    val numbersMismatch = todo.number1 != null || todo.number2 != null
    val numberColor = when {
        numbersMatch -> Color(0xFF1565C0)
        numbersMismatch -> Color(0xFFEF6C00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canCheck = numbersMatch
    Card(
        Modifier.fillMaxWidth().padding(vertical = (3f * scale).dp)
            .clickable(enabled = selectionMode != null) { onSelect() }
            .onGloballyPositioned { onBoundsChanged(it.boundsInParent()) }
            .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
            .shadow(if (isDragging) 12.dp else 0.dp, RoundedCornerShape((10f * scale).dp))
            .pointerInput(editable, selectionMode) {
                if (editable && selectionMode == null) detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                    onDragEnd = { onDragEnd() }, onDragCancel = { onDragCancel() }
                )
            },
        shape = RoundedCornerShape((10f * scale).dp),
        border = BorderStroke((1f * scale).dp, if (selectionMode != null) Color(0xFF7E57C2) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = (6f * scale).dp, vertical = (4f * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = done,
                enabled = editable && canCheck && selectionMode == null,
                onCheckedChange = onToggle,
                modifier = Modifier.size((24f * scale).dp)
            )
            Row(
                Modifier.weight(1f).clickable(enabled = editable && canCheck && selectionMode == null) { onToggle(!done) }
                    .padding(horizontal = (6f * scale).dp, vertical = (10f * scale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                todo.priority?.let {
                    Text("[$it]", fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = (6f * scale).dp))
                }
                Text(todo.text, fontSize = fontSize.sp, color = if (editable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VerticalDividerLine(scale)
            Box(Modifier.width((68f * scale).dp), contentAlignment = Alignment.Center) {
                Text(
                    todo.number1?.toString() ?: "＋",
                    fontSize = (fontSize * 0.9f).sp,
                    color = if (todo.number1 != null) numberColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            VerticalDividerLine(scale)
            TextButton(
                onClick = { onNumber(2) },
                enabled = editable && selectionMode == null,
                modifier = Modifier.width((68f * scale).dp)
            ) {
                Text(todo.number2?.toString() ?: "＋", fontSize = (fontSize * 0.9f).sp, color = if (todo.number2 != null) numberColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!editable) {
                Icon(Icons.Default.Lock, "과거 데이터", modifier = Modifier.padding(horizontal = (8f * scale).dp).size((22f * scale).dp))
            }
            }
            if (selectionMode != null) {
                Box(
                    Modifier.fillMaxSize().clickable { onSelect() }
                )
            }
        }
    }
}

@Composable
private fun VerticalDividerLine(scale: Float) {
    Box(Modifier.width(1.dp).height((28f * scale).dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun AddTodoDialog(onDismiss: () -> Unit, onAdd: (String, Int?, Int?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var priorityText by rememberSaveable { mutableStateOf("") }
    var number1Text by rememberSaveable { mutableStateOf("") }
    val textFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val priorityFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val number1Focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    fun save() {
        if (text.isNotBlank()) {
            onAdd(text.trim(), priorityText.toIntOrNull()?.takeIf { it >= 1 }, number1Text.toIntOrNull())
        }
    }
    LaunchedEffect(Unit) { textFocus.requestFocus(); keyboardController?.show() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("체크 항목 추가") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text("항목명") },
                    placeholder = { Text("체크할 항목을 입력하세요") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { priorityFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(textFocus)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = priorityText, onValueChange = { if (it.all(Char::isDigit) && it.length <= 3) priorityText = it },
                    label = { Text("우선순위") }, placeholder = { Text("미지정") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { number1Focus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(priorityFocus)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = number1Text, onValueChange = { if (it.all(Char::isDigit) && it.length <= 6) number1Text = it },
                    label = { Text("필요 숫자") }, placeholder = { Text("숫자를 입력하세요") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(number1Focus)
                )
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { save() }) { Text("추가") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun EditTodoDialog(
    initial: String,
    initialPriority: Int?,
    initialNumber1: Int?,
    onDismiss: () -> Unit,
    onSave: (String, Int?, Int?) -> Unit
) {
    var value by remember(initial) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var priorityText by remember(initialPriority) { mutableStateOf(initialPriority?.toString() ?: "") }
    var number1Text by remember(initialNumber1) { mutableStateOf(initialNumber1?.toString() ?: "") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboardController?.show() }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("체크 항목 수정") },
        text = {
            Column {
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, label = { Text("항목명") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = priorityText, onValueChange = { if (it.all(Char::isDigit) && it.length <= 3) priorityText = it },
                    label = { Text("우선순위") }, placeholder = { Text("미지정") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = number1Text, onValueChange = { if (it.all(Char::isDigit) && it.length <= 6) number1Text = it },
                    label = { Text("필요 숫자") }, placeholder = { Text("숫자를 입력하세요") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(enabled = value.text.isNotBlank(), onClick = { onSave(value.text.trim(), priorityText.toIntOrNull()?.takeIf { it >= 1 }, number1Text.toIntOrNull()) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun NumberInputDialog(title: String, initial: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial?.toString() ?: "") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    fun save() { onSave(text.toIntOrNull()) }
    LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboardController?.show() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.all(Char::isDigit) && it.length <= 6) text = it },
                label = { Text("숫자") },
                placeholder = { Text("숫자를 입력하세요") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        },
        confirmButton = { TextButton(onClick = { save() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = { onSave(null) }) { Text("기록 지우기") } }
    )
}

