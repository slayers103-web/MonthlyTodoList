package com.example.monthlytodolist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

data class MonthlyTodo(
    val id: String,
    var text: String,
    var isDone: Boolean
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
                    MonthlyHistoryTodoApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyHistoryTodoApp() {
    val context = LocalContext.current
    val pref = remember { context.getSharedPreferences("MonthlyHistoryPref", Context.MODE_PRIVATE) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        NotificationScheduler.scheduleMonthlyAlarms(context)
    }

    val actualCurrentMonth = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }
    var viewCalendar by remember { mutableStateOf(Calendar.getInstance()) }

    val selectedMonth = remember(viewCalendar.timeInMillis) {
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(viewCalendar.time)
    }

    var todoList by remember { mutableStateOf(listOf<MonthlyTodo>()) }
    var newTaskText by remember { mutableStateOf("") }

    LaunchedEffect(selectedMonth) {
        todoList = loadMonthData(pref, selectedMonth)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportBackupData(context, pref, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            importBackupData(context, pref, it) {
                todoList = loadMonthData(pref, selectedMonth)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = {
                            val cal = viewCalendar.clone() as Calendar
                            cal.add(Calendar.MONTH, -1)
                            viewCalendar = cal
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전달")
                        }

                        Text(
                            text = if (selectedMonth == actualCurrentMonth) "$selectedMonth (이번달)" else selectedMonth,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(onClick = {
                            val cal = viewCalendar.clone() as Calendar
                            cal.add(Calendar.MONTH, 1)
                            viewCalendar = cal
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "다음달")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // 1. 입력 창
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    label = { Text("새 할 일 추가") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newTaskText.isNotBlank()) {
                            val newItem = MonthlyTodo(
                                id = UUID.randomUUID().toString(),
                                text = newTaskText,
                                isDone = false
                            )
                            todoList = todoList + newItem
                            saveMonthData(pref, selectedMonth, todoList)
                            updateMasterList(pref, todoList)
                            newTaskText = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "추가")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 리스트 (화면 중간 채우기)
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(todoList, key = { it.id }) { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = item.isDone,
                            onCheckedChange = { isChecked ->
                                todoList = todoList.map {
                                    if (it.id == item.id) it.copy(isDone = isChecked) else it
                                }
                                saveMonthData(pref, selectedMonth, todoList)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.text,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                todoList = todoList.filter { it.id != item.id }
                                saveMonthData(pref, selectedMonth, todoList)
                                updateMasterList(pref, todoList)
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. 백업/복원 버튼 (맨 아래 고정)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        val fileName = "MonthlyTodo_Backup_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.json"
                        exportLauncher.launch(fileName)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("데이터 백업")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("데이터 복원")
                }
            }
        }
    }
}

private fun exportBackupData(context: Context, pref: android.content.SharedPreferences, uri: Uri) {
    try {
        val rootObj = JSONObject()
        pref.all.forEach { (key, value) ->
            if (value is String) rootObj.put(key, value)
        }
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(rootObj.toString().toByteArray())
        }
        Toast.makeText(context, "백업 저장 완료!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "백업 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun importBackupData(context: Context, pref: android.content.SharedPreferences, uri: Uri, onComplete: () -> Unit) {
    try {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) stringBuilder.append(line)
            }
        }
        val rootObj = JSONObject(stringBuilder.toString())
        val editor = pref.edit().clear()
        val keys = rootObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            editor.putString(key, rootObj.getString(key))
        }
        editor.apply()
        Toast.makeText(context, "복원 완료!", Toast.LENGTH_SHORT).show()
        onComplete()
    } catch (e: Exception) {
        Toast.makeText(context, "복원 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun loadMonthData(pref: android.content.SharedPreferences, monthKey: String): List<MonthlyTodo> {
    val jsonString = pref.getString("data_$monthKey", null)
    return if (jsonString != null) {
        parseJsonToTodoList(jsonString)
    } else {
        val masterJson = pref.getString("master_todo_list", null)
        val defaultList = if (masterJson != null) {
            parseJsonToTodoList(masterJson).map { it.copy(isDone = false) }
        } else {
            listOf(
                MonthlyTodo(UUID.randomUUID().toString(), "월세/관리비 납부", false),
                MonthlyTodo(UUID.randomUUID().toString(), "정기 구독 점검", false)
            )
        }
        saveMonthData(pref, monthKey, defaultList)
        defaultList
    }
}

private fun saveMonthData(pref: android.content.SharedPreferences, monthKey: String, list: List<MonthlyTodo>) {
    val jsonArray = JSONArray()
    list.forEach { item ->
        val obj = JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("isDone", item.isDone)
        }
        jsonArray.put(obj)
    }
    pref.edit().putString("data_$monthKey", jsonArray.toString()).apply()
}

private fun updateMasterList(pref: android.content.SharedPreferences, list: List<MonthlyTodo>) {
    val jsonArray = JSONArray()
    list.forEach { item ->
        val obj = JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("isDone", false)
        }
        jsonArray.put(obj)
    }
    pref.edit().putString("master_todo_list", jsonArray.toString()).apply()
}

private fun parseJsonToTodoList(jsonString: String): List<MonthlyTodo> {
    val list = mutableListOf<MonthlyTodo>()
    val jsonArray = JSONArray(jsonString)
    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
            MonthlyTodo(
                id = obj.getString("id"),
                text = obj.getString("text"),
                isDone = obj.getBoolean("isDone")
            )
        )
    }
    return list
}
