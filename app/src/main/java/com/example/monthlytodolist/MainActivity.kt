package com.example.monthlytodolist

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var todoText by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    // 월별 할 일 목록 데이터 (실제 저장 로직 연결 전 임시 상태)
    val todoList = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "매월 반복 To-Do List") },
                actions = {
                    // 백업 / 복원 더보기 메뉴
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("데이터 백업") },
                            onClick = {
                                menuExpanded = false
                                Toast.makeText(context, "백업 기능이 실행됩니다.", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("데이터 복원") },
                            onClick = {
                                menuExpanded = false
                                Toast.makeText(context, "복원 기능이 실행됩니다.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 1. 상단 월 변경 컨트롤 (이전달 / YYYY년 MM월 / 다음달)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "이전 달")
                }
                
                Text(
                    text = currentYearMonth.format(DateTimeFormatter.ofPattern("yyyy년 MM월")),
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "다음 달")
                }
            }

            // 2. 할 일 입력창 & 추가 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = todoText,
                    onValueChange = { todoText = it },
                    label = { Text("매월 할 일 입력") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (todoText.isNotBlank()) {
                            todoList.add(todoText)
                            todoText = ""
                        }
                    }
                ) {
                    Text("추가")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 할 일 목록 리스트
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(todoList) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
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
    val sharedPref = remember { context.getSharedPreferences("monthly_todo_pref", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    var todoList by remember { mutableStateOf(listOf<TodoItem>()) }
    var inputText by remember { mutableStateOf("") }
    var nextId by remember { mutableIntStateOf(1) }

    // 데이터 저장 함수
    fun saveTodoList(newList: List<TodoItem>) {
        todoList = newList
        val json = gson.toJson(newList)
        sharedPref.edit().putString("todo_list_json", json).apply()
    }

    // 앱 실행 시 저장된 데이터 불러오기 및 매월 1일 체크 해제 로직
    LaunchedEffect(Unit) {
        val json = sharedPref.getString("todo_list_json", null)
        var loadedList = if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<TodoItem>>() {}.type
            gson.fromJson<List<TodoItem>>(json, type) ?: emptyList()
        } else {
            emptyList()
        }

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) // 0~11
        val lastSavedMonth = sharedPref.getInt("last_saved_month", -1)

        // 달이 바뀐 경우 (예: 8월 -> 9월) 모든 체크박스 자동 해제
        if (lastSavedMonth != -1 && lastSavedMonth != currentMonth) {
            loadedList = loadedList.map { it.copy(isDone = false) }
        }

        // ID 중복 방지를 위한 nextId 설정
        nextId = (loadedList.maxOfOrNull { it.id } ?: 0) + 1

        // 상태 업데이트 및 월 정보 갱신 저장
        saveTodoList(loadedList)
        sharedPref.edit().putInt("last_saved_month", currentMonth).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("매월 반복 To-Do List") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 할 일 입력 영역
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("매월 할 일 입력") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val newItem = TodoItem(id = nextId++, text = inputText)
                            saveTodoList(todoList + newItem)
                            inputText = ""
                        }
                    }
                ) {
                    Text("추가")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // To-Do 목록 영역 (체크 및 삭제 지원)
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(todoList, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isDone,
                            onCheckedChange = { isChecked ->
                                val updated = todoList.map {
                                    if (it.id == item.id) it.copy(isDone = isChecked) else it
                                }
                                saveTodoList(updated)
                            }
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                val updated = todoList.filter { it.id != item.id }
                                saveTodoList(updated)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "삭제"
                            )
                        }
                    }
                }
            }
        }
    }
}
