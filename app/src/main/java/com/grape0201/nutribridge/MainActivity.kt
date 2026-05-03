package com.grape0201.nutribridge

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.NutritionRecord
import com.grape0201.nutribridge.ui.theme.NutriBridgeTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class Screen(val title: String) {
    Registration("Register"),
    History("History"),
    Settings("Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriBridgeTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(Screen.Registration) }
    val context = LocalContext.current
    val healthConnectManager = remember { HealthConnectManager(context) }
    val apiKeyManager = remember { ApiKeyManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (!healthConnectManager.hasAllPermissions()) {
            permissionLauncher.launch(healthConnectManager.permissions)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.Registration,
                    onClick = { currentScreen = Screen.Registration },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text(Screen.Registration.title) }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.History,
                    onClick = { currentScreen = Screen.History },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(Screen.History.title) }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Settings,
                    onClick = { currentScreen = Screen.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(Screen.Settings.title) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Registration -> MealRegistrationScreen(
                    healthConnectManager = healthConnectManager,
                    apiKeyManager = apiKeyManager,
                    onShowSnackbar = { scope.launch { snackbarHostState.showSnackbar(it) } },
                    onRequestPermissions = { permissionLauncher.launch(healthConnectManager.permissions) }
                )
                Screen.History -> MealHistoryScreen(
                    healthConnectManager = healthConnectManager,
                    onRequestPermissions = { permissionLauncher.launch(healthConnectManager.permissions) }
                )
                Screen.Settings -> ApiKeyScreen(apiKeyManager)
            }
        }
    }
}

@Composable
fun MealRegistrationScreen(
    healthConnectManager: HealthConnectManager,
    apiKeyManager: ApiKeyManager,
    onShowSnackbar: (String) -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mealResponse by remember { mutableStateOf<MealResponse?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val savingMeals = remember { mutableStateMapOf<Int, Boolean>() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val source = ImageDecoder.createSource(context.contentResolver, it)
            selectedBitmap = ImageDecoder.decodeBitmap(source)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("What did you eat?") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
            Text("Pick Photo")
        }

        selectedBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp)
            )
        }

        Button(
            onClick = {
                keyboardController?.hide()
                val apiKey = apiKeyManager.getApiKey()
                if (apiKey == null) {
                    onShowSnackbar("Please set Gemini API Key in Settings.")
                    return@Button
                }
                scope.launch {
                    isAnalyzing = true
                    savingMeals.clear()
                    val geminiManager = GeminiManager(apiKey)
                    val eatenAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    mealResponse = geminiManager.analyzeMeal(inputText, selectedBitmap, eatenAt)
                    if (mealResponse == null) {
                        onShowSnackbar("Failed to analyze meal.")
                    }
                    isAnalyzing = false
                }
            },
            enabled = !isAnalyzing && (inputText.isNotEmpty() || selectedBitmap != null),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isAnalyzing) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            else Text("Analyze with Gemini")
        }

        mealResponse?.meals?.forEachIndexed { index, info ->
            Card(modifier = Modifier.padding(top = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Result: ${info.menu}", style = MaterialTheme.typography.titleMedium)
                    Text("Calories: ${info.calories} kcal")
                    Text("Protein: ${info.protein} g")
                    Text("Fat: ${info.fat} g")
                    Text("Carbs: ${info.carb} g")
                    info.sourceUrl?.let {
                        Text("Source: $it", style = MaterialTheme.typography.bodySmall)
                    }

                    val isSaving = savingMeals[index] ?: false
                    Button(
                        onClick = {
                            scope.launch {
                                if (healthConnectManager.hasAllPermissions()) {
                                    savingMeals[index] = true
                                    healthConnectManager.writeNutritionRecord(
                                        info.calories.toDouble(),
                                        info.protein,
                                        info.fat,
                                        info.carb,
                                        info.menu,
                                        Instant.now()
                                    )
                                    onShowSnackbar("Saved ${info.menu} to Health Connect.")
                                    savingMeals[index] = false
                                    
                                    val updatedMeals = mealResponse?.meals?.toMutableList()?.apply {
                                        removeAt(index)
                                    }
                                    mealResponse = if (updatedMeals.isNullOrEmpty()) null else MealResponse(updatedMeals)
                                } else {
                                    onRequestPermissions()
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save to Health Connect")
                        }
                    }
                }
            }
        }
        
        if (mealResponse != null) {
            Button(
                onClick = {
                    mealResponse = null
                    inputText = ""
                    selectedBitmap = null
                },
                modifier = Modifier.padding(top = 16.dp),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text("Clear Results")
            }
        }
    }
}

@Composable
fun MealHistoryScreen(
    healthConnectManager: HealthConnectManager,
    onRequestPermissions: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var mealRecords by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }
    var hasPermissions by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    LaunchedEffect(selectedDate, refreshTrigger) {
        if (healthConnectManager.hasAllPermissions()) {
            hasPermissions = true
            val startOfDay = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            mealRecords = healthConnectManager.readNutritionRecords(startOfDay, endOfDay)
        } else {
            hasPermissions = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Meal History", style = MaterialTheme.typography.headlineSmall)
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
            }
            Text(
                text = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
            }
        }
        
        if (!hasPermissions) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Health Connect permissions required.")
                    Button(onClick = onRequestPermissions, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Grant Permissions")
                    }
                }
            }
        } else {
            val totalEnergy = mealRecords.sumOf { it.energy?.inKilocalories ?: 0.0 }
            val totalProtein = mealRecords.sumOf { it.protein?.inGrams ?: 0.0 }
            val totalFat = mealRecords.sumOf { it.totalFat?.inGrams ?: 0.0 }
            val totalCarbs = mealRecords.sumOf { it.totalCarbohydrate?.inGrams ?: 0.0 }

            if (mealRecords.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Daily Summary", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Energy: ${"%.0f".format(totalEnergy)} kcal")
                                Text("Protein: ${"%.1f".format(totalProtein)} g")
                            }
                            Column {
                                Text("Fat: ${"%.1f".format(totalFat)} g")
                                Text("Carbs: ${"%.1f".format(totalCarbs)} g")
                            }
                        }
                    }
                }
            }

            if (mealRecords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No records found for this date.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(mealRecords) { record ->
                        ListItem(
                            headlineContent = {
                                val time = record.startTime.atZone(ZoneId.systemDefault()).format(timeFormatter)
                                Text("[$time] ${record.name ?: "Unknown Meal"}")
                            },
                            supportingContent = {
                                val energy = record.energy?.inKilocalories?.let { "%.0f".format(it) } ?: "0"
                                val p = record.protein?.inGrams?.let { "%.1f".format(it) } ?: "0"
                                val f = record.totalFat?.inGrams?.let { "%.1f".format(it) } ?: "0"
                                val c = record.totalCarbohydrate?.inGrams?.let { "%.1f".format(it) } ?: "0"
                                Text("$energy kcal | P: ${p}g F: ${f}g C: ${c}g")
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    scope.launch {
                                        healthConnectManager.deleteNutritionRecord(record.metadata.id)
                                        refreshTrigger++
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApiKeyScreen(apiKeyManager: ApiKeyManager) {
    var apiKeyInput by remember { mutableStateOf("") }
    var savedKey by remember { mutableStateOf(apiKeyManager.getApiKey() ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Gemini API Key",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("Enter Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = {
                apiKeyManager.saveApiKey(apiKeyInput)
                savedKey = apiKeyInput
                apiKeyInput = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save API Key")
        }
        
        if (savedKey.isNotEmpty()) {
            Text(
                text = "API Key is saved securely.",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
            Button(
                onClick = {
                    apiKeyManager.clearApiKey()
                    savedKey = ""
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Clear Saved Key")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    NutriBridgeTheme {
        MainScreen()
    }
}
