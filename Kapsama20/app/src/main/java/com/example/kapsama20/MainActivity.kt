package com.example.kapsama20

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.net.Uri
import android.content.Intent
import android.widget.MediaController
import kotlin.random.Random

data class Student(val name: String, val initials: String, val className: String, val number: String)
data class CourseRecord(val course: String, val schedule: String, val teacher: String, val status: String)
data class AbsenceReport(val teacher: String, val note: String, val sentAt: Long)
data class RadioReading(
    val rsrp: Int?, val sinr: Int?, val rsrq: Int?, val technology: String,
    val mbps: Double? = null, val isDemo: Boolean = false
)

private val student = Student("Ali", "AE", "11-B", "482")
private val records = listOf(
    CourseRecord("Fizik Laboratuvarı", "Salı • 09:40", "Ahmet Hoca", "Kaçırıldı"),
    CourseRecord("Matematik", "Dün • 11:30", "Ayşe Hoca", "Kaçırıldı"),
    CourseRecord("Kimya", "Bugün • 10:20", "Mehmet Hoca", "İnceleniyor"),
    CourseRecord("Türkçe", "Pazartesi • 13:00", "Elif Hoca", "Katıldı")
)

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCellPermission.value = granted
    }
    private val hasCellPermission = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasCellPermission.value = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        setContent {
            StudentTheme {
                StudentApp(
                    hasCellPermission = hasCellPermission.value,
                    requestPermission = { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                )
            }
        }
    }
}

@Composable
private fun StudentTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme(primary = Color(0xFFD0BCFF), secondary = Color(0xFFB5D6A7))
        else lightColorScheme(primary = Color(0xFF6750A4), secondary = Color(0xFF386A20))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun StudentApp(hasCellPermission: Boolean, requestPermission: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("student", Context.MODE_PRIVATE) }
    var className by remember { mutableStateOf(prefs.getString("class_name", student.className) ?: student.className) }
    val currentStudent = student.copy(className = className)
    var section by remember { mutableStateOf("A") }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(false) }
    val emulator = remember { android.os.Build.FINGERPRINT.contains("generic", true) || android.os.Build.MODEL.contains("sdk", true) }
    var demoMode by remember { mutableStateOf(emulator) }
    var measuring by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<RadioReading?>(null) }
    var message by remember { mutableStateOf("") }
    var acildi by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<AbsenceReport?>(null) }
    var showNewLog by remember { mutableStateOf(false) }
    val localRecords = remember { mutableStateListOf<CourseRecord>() }
    var homework by remember { mutableStateOf<List<Homework>>(emptyList()) }
    var homeworkLoading by remember { mutableStateOf(true) }
    var homeworkMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var uploadId by remember { mutableStateOf<java.util.UUID?>(null) }
    var uploadIsDemo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { section = prefs.getString("section", "A") ?: "A" }

    suspend fun refreshHomework() {
        homeworkLoading = true
        val result = HomeworkRepository.load(context)
        homework = result.items
        homeworkMessage = result.message
        homeworkLoading = false
    }

    LaunchedEffect(Unit) { refreshHomework() }

    LaunchedEffect(uploadId, uploadIsDemo) {
        val id = uploadId ?: return@LaunchedEffect
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id).collect { info ->
            message = when (info?.state) {
                WorkInfo.State.SUCCEEDED -> if (uploadIsDemo) "DEMO: Supabase'e gönderildi." else "Supabase'e gönderildi."
                WorkInfo.State.FAILED -> info.outputData.getString("error") ?: "Gönderim başarısız."
                WorkInfo.State.RUNNING -> "Supabase'e gönderiliyor..."
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Ölçüm kuyrukta; bağlantı bekleniyor."
                WorkInfo.State.CANCELLED -> "Gönderim iptal edildi."
                null -> message
            }
        }
    }

    suspend fun measure() {
        if (measuring || (!hasCellPermission && !demoMode)) return
        measuring = true
        try {
            val value = if (demoMode) demoReading() else withContext(Dispatchers.IO) { readRadio(context) }
            reading = value
            if (value == null || listOf(value.rsrp, value.sinr, value.rsrq).all { it == null }) {
                message = "Hücre ölçümü kullanılamıyor; kayıt oluşturulmadı."
            } else {
                val work = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(
                        "section" to section,
                        "rsrp" to (value.rsrp ?: Int.MIN_VALUE),
                        "sinr" to (value.sinr ?: Int.MIN_VALUE),
                        "rsrq" to (value.rsrq ?: Int.MIN_VALUE),
                        "mbps" to (value.mbps ?: Double.NaN),
                        "demo" to value.isDemo,
                        "time" to System.currentTimeMillis()
                    ))
                    .build()
                if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
                    message = "Ölçüm alındı. Supabase ayarlanmadığı için gönderilmedi."
                } else {
                    WorkManager.getInstance(context).enqueue(work)
                    uploadIsDemo = value.isDemo
                    uploadId = work.id
                    message = if (value.isDemo) "DEMO: Supabase kuyruğuna alındı." else "Ölçüm yerel kuyruğa alındı; bağlantı varsa gönderilecek."
                }
            }
        } catch (e: SecurityException) {
            message = "Hücre verisi izni gerekli."
        } finally {
            measuring = false
        }
    }

    LaunchedEffect(auto, hasCellPermission, section, demoMode) {
        while (auto && (hasCellPermission || demoMode)) {
            measure()
            delay(10_000)
        }
    }

    LaunchedEffect(hasCellPermission, demoMode) {
        while (hasCellPermission && !demoMode) {
            try { reading = withContext(Dispatchers.IO) { readRadio(context) } }
            catch (_: SecurityException) { reading = null }
            delay(2_000)
        }
    }

    Scaffold(
        topBar = { TopBar(currentStudent, query, { query = it }) },
        bottomBar = {
            NavigationBar(modifier = Modifier.height(80.dp)) {
                listOf("Derslerim", "Yoklama", "Rapor").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(listOf("▦", "◉", "▤")[index]) },
                        label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(
                onClick = { showNewLog = true },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(
                Modifier.padding(padding), currentStudent,
                (records + localRecords).filter {
                    it.course.contains(query, ignoreCase = true) || it.teacher.contains(query, ignoreCase = true)
                }, notice,
                onNotice = { showNotice = true }, acildi = acildi,
                homework = homework, homeworkLoading = homeworkLoading,
                homeworkMessage = homeworkMessage,
                onClassName = { selected ->
                    className = selected
                    prefs.edit().putString("class_name", selected).apply()
                },
                onRefreshHomework = { scope.launch { refreshHomework() } },
                onVideoStarted = { acildi = true }
            )
            1 -> MeasurementScreen(
                Modifier.padding(padding), section, { chosen ->
                    section = chosen
                    prefs.edit().putString("section", chosen).apply()
                }, reading, measuring, auto, { auto = it }, demoMode, { selected ->
                    demoMode = selected
                    reading = null
                    message = if (selected) "DEMO: Değerler temsili olacak." else ""
                },
                hasCellPermission, requestPermission, onMeasure = { scope.launch { measure() } },
                message = message
            )
            else -> ReportScreen(Modifier.padding(padding), records + localRecords, notice)
        }
    }
    if (showNotice) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNotice = false },
            title = { Text("Öğretmene bildir") },
            text = { OutlinedTextField(note, { note = it }, label = { Text("Açıklama") }) },
            confirmButton = { TextButton(onClick = {
                notice = AbsenceReport("Ahmet Hoca", note, System.currentTimeMillis())
                showNotice = false
            }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { showNotice = false }) { Text("Vazgeç") } }
        )
    }
    if (showNewLog) {
        var course by remember { mutableStateOf("") }
        var teacher by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewLog = false },
            title = { Text("Yeni yoklama kaydı") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(course, { course = it }, label = { Text("Ders") })
                OutlinedTextField(teacher, { teacher = it }, label = { Text("Öğretmen") })
            } },
            confirmButton = { TextButton(enabled = course.isNotBlank(), onClick = {
                localRecords.add(CourseRecord(course.trim(), "Şimdi", teacher.trim(), "İnceleniyor"))
                showNewLog = false
            }) { Text("Ekle") } },
            dismissButton = { TextButton(onClick = { showNewLog = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun TopBar(student: Student, query: String, onQuery: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query, onValueChange = onQuery, singleLine = true,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Ders, öğretmen veya...") },
                leadingIcon = { Text("☰", style = MaterialTheme.typography.titleLarge) },
                trailingIcon = { Text("🎙") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(student.initials, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, student: Student, records: List<CourseRecord>, notice: AbsenceReport?,
    onNotice: () -> Unit, acildi: Boolean,
    homework: List<Homework>, homeworkLoading: Boolean, homeworkMessage: String?,
    onClassName: (String) -> Unit,
    onRefreshHomework: () -> Unit, onVideoStarted: () -> Unit
) {
    val visibleHomework = homework.filter { item ->
        item.section == null || item.section.equals("Tüm şubeler", ignoreCase = true) ||
            item.section.equals(student.className, ignoreCase = true)
    }
    val availableClasses = (listOf(student.className) + homework.mapNotNull { it.section }
        .filterNot { it.equals("Tüm şubeler", ignoreCase = true) }).distinct()
    var classMenuExpanded by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("${student.className} SINIFI • NO: ${student.number}", color = MaterialTheme.colorScheme.primary)
            Text("İyi günler, ${student.name}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Haftalık Devamsızlık & Durum", style = MaterialTheme.typography.titleLarge)
                    Text("Bu hafta 2 kaçırılan ders tespit edildi. Toplam devamsızlık: 3.5 gün (Sınır: 10 gün)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("2 Kaçırılan Ders") })
                        AssistChip(onClick = {}, label = { Text("Son: Fizik Lab") })
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("⚠", "2 Ders", "Kaçırılan Dersler", "Fizik (Salı), Mat (Dün)", Modifier.weight(1f), {})
                ActionCard("➤", if (notice == null) "Bildir" else "Taslak", "Öğretmene Bildir", "Ahmet Hoca • 09:40", Modifier.weight(1f), onNotice)
            }
        }
        item { Text("Ders Katılım Geçmişi", style = MaterialTheme.typography.titleLarge) }
        items(records) { record -> CourseRow(record) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Yayınlanan Ödevler", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onRefreshHomework, enabled = !homeworkLoading) { Text("Yenile") }
            }
            Box {
                TextButton(onClick = { classMenuExpanded = true }) { Text("Şube: ${student.className} ▾") }
                DropdownMenu(expanded = classMenuExpanded, onDismissRequest = { classMenuExpanded = false }) {
                    availableClasses.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            onClassName(option)
                            classMenuExpanded = false
                        })
                    }
                }
            }
            if (homeworkLoading) CircularProgressIndicator()
            if (homeworkMessage != null) Text(homeworkMessage, color = MaterialTheme.colorScheme.primary)
            if (!homeworkLoading && visibleHomework.isEmpty()) Text("Bu şube için ödev yok.")
        }
        items(visibleHomework, key = { "homework-${it.id}" }) { item -> HomeworkCard(item) }
        item {
            Text("Cihazdaki Ders Videosu", style = MaterialTheme.typography.titleLarge)
            OfflineVideo(onStarted = onVideoStarted)
            if (acildi) Text("Video açıldı", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HomeworkCard(homework: Homework) {
    val context = LocalContext.current
    val videoUrl = homework.videoUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(homework.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val target = listOfNotNull(homework.type, homework.section).joinToString(" • ")
            if (target.isNotEmpty()) Text(target, style = MaterialTheme.typography.bodySmall)
            if (videoUrl == null) {
                Text("Video bağlantısı yok.", style = MaterialTheme.typography.bodySmall)
            } else {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                    runCatching { context.startActivity(intent) }
                }) { Text("Bağlantıyı aç") }
            }
        }
    }
}

@Composable
private fun ActionCard(icon: String, badge: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon)
            Text(badge, color = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CourseRow(record: CourseRecord) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.course, fontWeight = FontWeight.Bold)
                Text("${record.schedule} • ${record.teacher}", style = MaterialTheme.typography.bodySmall)
            }
            SuggestionChip(onClick = {}, label = { Text(record.status) })
        }
    }
}

@Composable
private fun MeasurementScreen(
    modifier: Modifier, section: String, onSection: (String) -> Unit, reading: RadioReading?,
    measuring: Boolean, auto: Boolean, onAuto: (Boolean) -> Unit,
    demoMode: Boolean, onDemo: (Boolean) -> Unit,
    hasCellPermission: Boolean, requestPermission: () -> Unit,
    onMeasure: () -> Unit, message: String
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Yoklama ve Sinyal Ölçümü", style = MaterialTheme.typography.headlineSmall)
        Text("Şebeke: ${reading?.technology ?: if (demoMode) "Demo" else "Kullanılamıyor"}", color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Demo değerleri (temsili)", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Switch(checked = demoMode, onCheckedChange = onDemo)
        }
        Text("Öğrenci seçimi")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("A", "B", "C").forEachIndexed { index, value ->
                SegmentedButton(selected = section == value, onClick = { onSection(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3), label = { Text(value) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("RSRP", reading?.rsrp?.let { "$it dBm" } ?: "Veri yok", Modifier.weight(1f))
            MetricCard("SINR", reading?.sinr?.let { "$it dB" } ?: "Veri yok", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("RSRQ", reading?.rsrq?.let { "$it dB" } ?: "Veri yok", Modifier.weight(1f))
            MetricCard("Hız", reading?.mbps?.let { "%.1f Mbps".format(it) } ?: "Veri yok", Modifier.weight(1f))
        }
        if (!demoMode && reading != null && (reading.sinr == null || reading.rsrq == null || reading.mbps == null)) {
            Text(
                "Cihaz bazı sinyal değerlerini bildirmiyor. Hız testi yapılmadığından gerçek Mbps verisi yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!hasCellPermission && !demoMode) Button(onClick = requestPermission) { Text("Hücre verisi izni ver") }
        Button(
            onClick = onMeasure, enabled = (hasCellPermission || demoMode) && !measuring,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            if (measuring) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary)
            else Text("Ölç", style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Otomatik mod", fontWeight = FontWeight.Bold)
                Text("Uygulama açıkken her 10 saniyede bir")
            }
            Switch(checked = auto, onCheckedChange = onAuto, enabled = hasCellPermission || demoMode)
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReportScreen(modifier: Modifier, records: List<CourseRecord>, notice: AbsenceReport?) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Rapor", style = MaterialTheme.typography.headlineSmall)
        Text("Kaçırılan ders: ${records.count { it.status == "Kaçırıldı" }}")
        Text("Öğretmen bildirimi: ${if (notice == null) "Henüz yok" else "Taslak"}")
    }
}

@Composable
private fun OfflineVideo(onStarted: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("student", Context.MODE_PRIVATE) }
    var uri by remember { mutableStateOf(prefs.getString("video_uri", null)?.let(Uri::parse)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen ->
        if (chosen != null) {
            context.contentResolver.takePersistableUriPermission(chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("video_uri", chosen.toString()).apply()
            uri = chosen
        }
    }
    Button(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Cihazdaki videoyu seç") }
    val selected = uri ?: return
    AndroidView(factory = { ctx ->
        VideoView(ctx).apply {
            setVideoURI(selected)
            setMediaController(MediaController(ctx))
            setOnPreparedListener { start() }
            setOnInfoListener { _, what, _ ->
                if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onStarted()
                false
            }
        }
    }, update = { view -> if (view.tag != selected) {
        view.tag = selected
        view.setVideoURI(selected)
    } }, modifier = Modifier.fillMaxWidth().height(220.dp))
}

private fun readRadio(context: Context): RadioReading? {
    val manager = context.getSystemService(TelephonyManager::class.java) ?: return null
    val cells = manager.allCellInfo ?: return null
    fun valid(value: Int) = value.takeUnless { it == CellInfo.UNAVAILABLE }
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val nr = cells.filterIsInstance<CellInfoNr>().firstOrNull { it.isRegistered }
        if (nr != null) {
            val signal = manager.signalStrength
                ?.getCellSignalStrengths(CellSignalStrengthNr::class.java)?.firstOrNull()
                ?: (nr.cellSignalStrength as CellSignalStrengthNr)
            return RadioReading(valid(signal.ssRsrp), valid(signal.ssSinr), valid(signal.ssRsrq), "5G NR")
        }
    }
    val lte = cells.filterIsInstance<CellInfoLte>().firstOrNull { it.isRegistered } ?: return null
    val signal = if (android.os.Build.VERSION.SDK_INT >= 29) {
        manager.signalStrength?.getCellSignalStrengths(CellSignalStrengthLte::class.java)?.firstOrNull()
            ?: lte.cellSignalStrength
    } else lte.cellSignalStrength
    return RadioReading(valid(signal.rsrp), valid(signal.rssnr), valid(signal.rsrq), "4G LTE")
}

private fun demoReading(): RadioReading {
    val quality = Random.nextInt(15, 96)
    return RadioReading(
        rsrp = -115 + (quality * 0.4).toInt(),
        sinr = -2 + (quality * 0.27).toInt(),
        rsrq = -19 + (quality * 0.12).toInt(),
        technology = "DEMO • temsili",
        mbps = Random.nextInt(50, 801) / 10.0,
        isDemo = true
    )
}
