package com.example.monthlytodolist

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

data class TodoItem(
    val id: Int,
    val text: String,
    val isDone: Boolean = false
)

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
