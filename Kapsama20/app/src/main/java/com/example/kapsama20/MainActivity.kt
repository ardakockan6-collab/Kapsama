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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import android.net.Uri
import android.content.Intent
import android.widget.MediaController
import kotlin.random.Random

data class Student(
    val name: String, val initials: String, val className: String, val number: String,
    val gradeLevel: Int? = null
)
data class CourseRecord(val course: String, val schedule: String, val teacher: String, val status: String)
data class AbsenceReport(val teacher: String, val note: String, val sentAt: Long)
data class RadioReading(
    val rsrp: Int?, val sinr: Int?, val rsrq: Int?, val technology: String,
    val mbps: Double? = null, val isDemo: Boolean = false,
    val upMbps: Double? = null,   // yükleme hızı
    val pingMs: Double? = null,   // gecikme
    val level: Int? = null,       // telefonun gösterdiği çubuk (0–4)
    val ag: String? = null        // "wifi" / "mobil"
)

private val records = emptyList<CourseRecord>()

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasCellPermission.value = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    private val hasCellPermission = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasCellPermission.value = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        setContent {
            StudentTheme {
                StudentApp(
                    hasCellPermission = hasCellPermission.value,
                    requestPermission = { permission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
                    )) }
                )
            }
        }
    }
}

@Composable
private fun StudentTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
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
    val scope = rememberCoroutineScope()
    var authSession by remember { mutableStateOf<AuthSession?>(null) }
    var checkingSession by remember { mutableStateOf(true) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginBusy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        authSession = AuthRepository.restore(context)
        checkingSession = false
    }
    if (checkingSession) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val session = authSession
    if (session == null) {
        LoginScreen(
            busy = loginBusy, error = loginError,
            onLogin = { email, password ->
                if (!loginBusy) scope.launch {
                    loginBusy = true
                    loginError = null
                    try { authSession = AuthRepository.signIn(context, email, password) }
                    catch (e: Exception) { loginError = e.message ?: "Giriş yapılamadı." }
                    finally { loginBusy = false }
                }
            }
        )
        return
    }
    val prefs = remember { context.getSharedPreferences("student", Context.MODE_PRIVATE) }
    var className by remember(session.id) {
        mutableStateOf(prefs.getString("class_name_${session.id}", session.className) ?: session.className)
    }
    val displayName = session.name
    val initials = displayName.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.take(1).uppercase() }.ifBlank { "Ö" }
    var gradeLevel by remember(session.id) { mutableStateOf<Int?>(null) }
    val currentStudent = Student(displayName, initials, className, session.studentNumber, gradeLevel)
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
    val submittedHomeworkIds = remember { mutableStateListOf<Long>() }
    val submittingHomeworkIds = remember { mutableStateListOf<Long>() }
    var uploadId by remember { mutableStateOf<java.util.UUID?>(null) }
    var uploadIsDemo by remember { mutableStateOf(false) }
    val downloadedIds = remember { mutableStateListOf<Long>() }
    var kota by remember(session.id) { mutableStateOf(KotaTercihi.oku(context, session.id)) }

    fun bendekiOdevler(liste: List<Homework>) = liste.filter { item ->
        item.section == null || item.section.equals("Tüm şubeler", ignoreCase = true) ||
            item.section.equals(className, ignoreCase = true)
    }

    fun indirilenleriTara() {
        val bulunan = homework.filter { OdevDosyalari.indirildiMi(context, it.id) }.map { it.id }
        downloadedIds.clear()
        downloadedIds.addAll(bulunan)
    }

    suspend fun refreshHomework() {
        homeworkLoading = true
        val result = HomeworkRepository.load(context)
        homework = result.items
        gradeLevel = HomeworkRepository.loadStudentGrade(context, session.id)
        val remoteSubmitted = HomeworkRepository.loadSubmittedIds(context, session.id)
        submittedHomeworkIds.clear()
        submittedHomeworkIds.addAll(remoteSubmitted)
        homeworkMessage = result.message
        homeworkLoading = false
        // Videolar Wi-Fi'a bağlanınca kendiliğinden iner; mesaj ödevleri liste ile zaten telefonda
        for (odev in bendekiOdevler(homework)) {
            if (odev.videoUrl == null) OdevDosyalari.mesajAlindi(context, session.id, odev.id)
            else OdevDosyalari.planla(context, session.id, odev)
        }
        indirilenleriTara()
    }

    // Ödev saati ölçümü: her akşam 19–23 arası arka planda
    LaunchedEffect(session.id) { AksamOlcumWorker.planla(context) }

    // Bir indirme bitince listeyi güncelle
    LaunchedEffect(Unit) {
        WorkManager.getInstance(context).getWorkInfosByTagFlow(OdevDosyalari.ETIKET).collect { indirilenleriTara() }
    }

    fun submitHomework(homeworkId: Long) {
        if (homeworkId in submittedHomeworkIds || homeworkId in submittingHomeworkIds) return
        val work = OneTimeWorkRequestBuilder<HomeworkSubmitWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(
                HomeworkSubmitWorker.KEY_HOMEWORK_ID to homeworkId,
                HomeworkSubmitWorker.KEY_STUDENT_ID to session.id,
                HomeworkSubmitWorker.KEY_TIME to System.currentTimeMillis()
            ))
            .addTag("homework-submit-${session.id}")
            .addTag("homework-id-$homeworkId")
            .build()
        submittingHomeworkIds.add(homeworkId)
        homeworkMessage = "Ödev öğretmene gönderilmek üzere kuyruğa alındı."
        WorkManager.getInstance(context).enqueueUniqueWork(
            "homework-${session.id}-$homeworkId",
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    LaunchedEffect(session.id) {
        WorkManager.getInstance(context).getWorkInfosByTagFlow("homework-submit-${session.id}").collect { jobs ->
            submittingHomeworkIds.clear()
            jobs.forEach { info ->
                val homeworkId = info.tags.firstOrNull { it.startsWith("homework-id-") }
                    ?.removePrefix("homework-id-")?.toLongOrNull() ?: return@forEach
                when {
                    !info.state.isFinished -> submittingHomeworkIds.add(homeworkId)
                    info.state == WorkInfo.State.SUCCEEDED -> {
                        if (homeworkId !in submittedHomeworkIds) submittedHomeworkIds.add(homeworkId)
                    }
                    info.state == WorkInfo.State.FAILED -> homeworkMessage = info.outputData.getString("error") ?: "Ödev gönderilemedi."
                }
            }
        }
    }

    LaunchedEffect(Unit) { refreshHomework() }

    LaunchedEffect(uploadId, uploadIsDemo) {
        val id = uploadId ?: return@LaunchedEffect
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id).collect { info ->
            message = when (info?.state) {
                WorkInfo.State.SUCCEEDED -> if (uploadIsDemo) "DEMO: Supabase'e gönderildi." else "Supabase'e gönderildi."
                WorkInfo.State.FAILED -> info.outputData.getString("error") ?: "Gönderim başarısız."
                WorkInfo.State.RUNNING -> "Supabase'e gönderiliyor..."
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Ölçüm kuyrukta; gönderim yeniden denenecek."
                WorkInfo.State.CANCELLED -> "Gönderim iptal edildi."
                null -> message
            }
        }
    }

    /* hizTestiYap: elle "Ölç" basınca true (≈3,5 MB veri); otomatik modda false (sadece sinyal, veri harcamaz) */
    suspend fun measure(hizTestiYap: Boolean) {
        if (measuring) return
        measuring = true
        try {
            var value = if (demoMode) demoReading() else if (hasCellPermission) withContext(Dispatchers.IO) {
                try { readRadio(context) } catch (_: SecurityException) { null }
            } else null
            if (!demoMode && hizTestiYap) {
                message = "Hız testi yapılıyor (yaklaşık 3,5 MB veri)…"
                val hiz = HizTesti.calistir(context)
                value = (value ?: RadioReading(null, null, null, "Bilinmiyor"))
                    .copy(mbps = hiz.indirmeMbps, upMbps = hiz.yuklemeMbps, pingMs = hiz.pingMs, ag = hiz.ag)
            }
            reading = value
            val okuma = value
            if (okuma == null || (listOf(okuma.rsrp, okuma.sinr, okuma.rsrq).all { it == null } && okuma.mbps == null && okuma.pingMs == null)) {
                message = "Ölçüm alınamadı; kayıt oluşturulmadı."
            } else {
                if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
                    message = "Ölçüm alındı. Supabase ayarlanmadığı için gönderilmedi."
                } else {
                    val work = olcumIsi(session.id, okuma)
                    WorkManager.getInstance(context).enqueue(work)
                    uploadIsDemo = okuma.isDemo
                    uploadId = work.id
                    message = when {
                        okuma.isDemo -> "DEMO: Supabase kuyruğuna alındı (simülasyon olarak işaretli)."
                        else -> "Ölçüm yerel kuyruğa alındı; bağlantı varsa gönderilecek."
                    }
                }
            }
        } catch (e: SecurityException) {
            message = "Hücre verisi izni gerekli."
        } finally {
            measuring = false
        }
    }

    var visible by remember { mutableStateOf(true) }
    DisposableEffect(context) {
        val lifecycle = (context as ComponentActivity).lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) visible = true
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) visible = false
        }
        visible = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(auto, visible, hasCellPermission, session.id, demoMode) {
        while (auto && visible && (hasCellPermission || demoMode)) {
            measure(hizTestiYap = false)
            delay(10_000)
        }
    }

    // Canlı sinyal göstergesi; son hız testinin sonuçları ekranda kalsın
    LaunchedEffect(hasCellPermission, demoMode) {
        while (hasCellPermission && !demoMode) {
            try {
                val yeni = withContext(Dispatchers.IO) { readRadio(context) }
                val eski = reading
                reading = if (yeni != null && eski != null) {
                    yeni.copy(mbps = eski.mbps, upMbps = eski.upMbps, pingMs = eski.pingMs, ag = eski.ag)
                } else yeni ?: eski
            } catch (_: SecurityException) { reading = null }
            delay(2_000)
        }
    }

    Scaffold(
        topBar = { TopBar(currentStudent, query, { query = it }, onSignOut = {
            AuthRepository.signOut(context)
            authSession = null
        }) },
        bottomBar = {
            NavigationBar(modifier = Modifier.height(80.dp)) {
                listOf("Ödevlerim", "Ölçüm").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { LearningIcon(measurement = index == 1) },
                        label = { Text(label) }
                    )
                }
            }
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
                submittedHomeworkIds = submittedHomeworkIds,
                submittingHomeworkIds = submittingHomeworkIds,
                onSubmitHomework = ::submitHomework,
                onClassName = { selected ->
                    className = selected
                    prefs.edit().putString("class_name_${session.id}", selected).apply()
                    scope.launch { refreshHomework() }
                },
                onRefreshHomework = { scope.launch { refreshHomework() } },
                onVideoStarted = { acildi = true },
                downloadedIds = downloadedIds,
                kota = kota,
                onKota = { secim ->
                    kota = secim
                    scope.launch {
                        KotaTercihi.kaydet(context, session.id, secim)
                        homeworkMessage = "Kota telefona kaydedildi; bağlantı gelince öğretmene gönderilecek."
                    }
                },
                onOpened = { odevId -> scope.launch { OdevDosyalari.acildi(context, session.id, odevId) } }
            )
            1 -> MeasurementScreen(
                Modifier.padding(padding), currentStudent.name, reading, measuring, auto, { auto = it }, demoMode, { selected ->
                    demoMode = selected
                    reading = null
                    message = if (selected) "DEMO: Değerler temsili olacak." else ""
                },
                hasCellPermission, requestPermission, onMeasure = { scope.launch { measure(hizTestiYap = true) } },
                message = message,
                onAksamDene = {
                    AksamOlcumWorker.simdiCalistir(context)
                    message = "Ödev saati ölçümü kuyruğa alındı; Android bağlantı ve pil koşullarına göre çalıştırır."
                }
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
private fun LoginScreen(busy: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.45f), colors.surface)))
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF8F7FC)) {
            YoklaMark(modifier = Modifier.padding(20.dp).size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("Eğitim her yerde.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Ödevlerin ve bağlantın, tek bir yerde.", color = colors.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Öğrenci girişi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-posta") },
                    shape = RoundedCornerShape(16.dp), singleLine = true
                )
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Şifre") },
                    shape = RoundedCornerShape(16.dp), singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                if (error != null) Text(error, color = colors.error)
                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                        color = colors.onPrimary)
                    else Text("Giriş yap", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Hesap bilgilerini öğretmeninizden alın.", style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
    }
}

@Composable
private fun TopBar(student: Student, query: String, onQuery: (String) -> Unit, onSignOut: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF8F7FC)) {
                YoklaMark(modifier = Modifier.padding(8.dp).size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Öğrenci alanı", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(student.initials, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            TextButton(onClick = onSignOut) { Text("Çıkış") }
        }
    }
}

/** Paylaşılan logonun dört çubuklu, çözünürlükten bağımsız simgesi. */
@Composable
private fun YoklaMark(modifier: Modifier = Modifier.size(32.dp)) {
    Icon(painter = painterResource(R.drawable.ic_yokla_mark), contentDescription = null,
        modifier = modifier, tint = Color.Unspecified)
}

/** Çizimler cihazda üretilir; görsel/font indirmek gerekmez. */
@Composable
private fun LearningIcon(measurement: Boolean = false, modifier: Modifier = Modifier.size(24.dp)) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        if (measurement) {
            listOf(0.35f, 0.58f, 0.82f).forEachIndexed { index, height ->
                val x = size.width * (0.22f + index * 0.28f)
                drawLine(color, Offset(x, size.height * 0.85f), Offset(x, size.height * (0.85f - height)),
                    strokeWidth = stroke * 2, cap = StrokeCap.Round)
            }
        } else {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.24f)
                lineTo(size.width * 0.12f, size.height * 0.12f)
                lineTo(size.width * 0.12f, size.height * 0.76f)
                lineTo(size.width * 0.5f, size.height * 0.88f)
                lineTo(size.width * 0.88f, size.height * 0.76f)
                lineTo(size.width * 0.88f, size.height * 0.12f)
                close()
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            drawLine(color, Offset(size.width * 0.5f, size.height * 0.24f),
                Offset(size.width * 0.5f, size.height * 0.88f), strokeWidth = stroke)
        }
    }
}

@Composable
private fun StatusPill(text: String, accent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(50),
        color = if (accent) colors.tertiaryContainer else colors.surfaceContainerHighest,
        contentColor = if (accent) colors.onTertiaryContainer else colors.onSurfaceVariant) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StudentHero(student: Student, homework: List<Homework>, submittedIds: List<Long>,
    downloadedIds: List<Long>, loading: Boolean, details: String) {
    val colors = MaterialTheme.colorScheme
    val completed = homework.count { it.id in submittedIds }
    val saved = homework.count { it.videoUrl != null && it.id in downloadedIds }
    Card(shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer)) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
                .drawBehind {
                    drawCircle(colors.primary.copy(alpha = 0.05f), size.width * 0.38f,
                        Offset(size.width * 1.05f, size.height * 0.08f))
                }.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ÖĞRENME ALANIN", style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer)
            Text("İyi günler, ${student.name}", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer)
            if (!loading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(homework.size to "Ödev", completed to "Gönderilen", saved to "İnen video").forEach { (count, label) ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                            color = colors.surface.copy(alpha = 0.8f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(count.toString(), style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold, color = colors.primary)
                                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurface)
                            }
                        }
                    }
                }
                if (homework.isNotEmpty()) {
                    LinearProgressIndicator(progress = { completed.toFloat() / homework.size },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color = colors.primary, trackColor = colors.onPrimaryContainer.copy(alpha = 0.12f))
                    Text("$completed / ${homework.size} ödev öğretmene gönderildi",
                        style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
                }
            } else Text("Ödevlerin yükleniyor…", style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer)
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, student: Student, records: List<CourseRecord>, notice: AbsenceReport?,
    onNotice: () -> Unit, acildi: Boolean,
    homework: List<Homework>, homeworkLoading: Boolean, homeworkMessage: String?,
    submittedHomeworkIds: List<Long>, submittingHomeworkIds: List<Long>,
    onSubmitHomework: (Long) -> Unit,
    onClassName: (String) -> Unit,
    onRefreshHomework: () -> Unit, onVideoStarted: () -> Unit,
    downloadedIds: List<Long>, kota: String?, onKota: (String) -> Unit,
    onOpened: (Long) -> Unit
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
            val studentDetails = listOfNotNull(
                // ogrenci_sinif tablosundaki değer okul sınıfı değil, evdeki bağlantı sınıfıdır (1–4)
                student.gradeLevel?.let { s ->
                    "Evden bağlantı: " + (listOf("Canlı ders", "Video", "Sadece mesaj", "Bağlantı yok").getOrNull(s - 1) ?: "?")
                },
                student.className.takeIf { it != "Şube seçilmedi" }?.let { "Şube: $it" },
                student.number.takeIf { it != "—" }?.let { "NO: $it" }
            ).joinToString(" • ")
            StudentHero(student, visibleHomework, submittedHomeworkIds, downloadedIds, homeworkLoading, studentDetails)
        }
        // Elle yazılmış devamsızlık/yoklama kartları kaldırıldı (gerçek veri değildi)
        item {
            // Mobil veri kotası: çekim iyi olsa bile kota yetmezse video izlenemez
            var kotaMenu by remember { mutableStateOf(false) }
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        TextButton(onClick = { kotaMenu = true }) {
                            Text("Aylık mobil veri kotan: ${kota ?: "seçilmedi"} ▾", fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = kotaMenu, onDismissRequest = { kotaMenu = false }) {
                            KotaTercihi.SECENEKLER.forEach { secim ->
                                DropdownMenuItem(text = { Text(secim) }, onClick = {
                                    onKota(secim)
                                    kotaMenu = false
                                })
                            }
                        }
                    }
                    Text(
                        "Videolar Android’in kotasız olarak işaretlediği ağda otomatik indirilir. Kotanı öğretmenle paylaşabilirsin.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
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
        items(visibleHomework, key = { "homework-${it.id}" }) { item ->
            HomeworkCard(
                homework = item,
                submitted = item.id in submittedHomeworkIds,
                submitting = item.id in submittingHomeworkIds,
                onSubmit = { onSubmitHomework(item.id) },
                downloaded = item.id in downloadedIds,
                kota = kota,
                onOpened = {
                    onVideoStarted()
                    onOpened(item.id)
                }
            )
        }
        // "Cihazdaki videoyu seç" kaldırıldı: videolar artık Wi-Fi'da kendiliğinden iner
    }
}

/* Son teslim: "12 Eki, 23:59" */
private fun tarihYaz(iso: String): String? = runCatching {
    java.time.OffsetDateTime.parse(iso)
        .atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.forLanguageTag("tr")))
}.getOrNull()

@Composable
private fun HomeworkCard(
    homework: Homework, submitted: Boolean, submitting: Boolean, onSubmit: () -> Unit,
    downloaded: Boolean, kota: String?, onOpened: () -> Unit
) {
    val context = LocalContext.current
    var oynat by remember { mutableStateOf(false) }
    val videoUrl = homework.videoUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    LearningIcon(modifier = Modifier.padding(12.dp).size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                StatusPill(when {
                    submitted -> "Gönderildi"
                    submitting -> "Kuyrukta"
                    downloaded -> "Telefonda hazır"
                    else -> homework.type ?: "Ödev"
                }, accent = submitted || downloaded)
            }
            Text(homework.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val target = listOfNotNull(homework.type, homework.section).joinToString(" • ")
            if (target.isNotEmpty()) Text(target, style = MaterialTheme.typography.bodySmall)
            homework.dueAt?.let(::tarihYaz)?.let {
                Text("Son teslim: $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            val boyut = homework.sizeMb?.let { "%.0f MB".format(it) }
            when {
                videoUrl == null -> Text("Videosuz ödev; metni telefonunda.", style = MaterialTheme.typography.bodySmall)
                downloaded -> {
                    Button(onClick = { oynat = true }, modifier = Modifier.fillMaxWidth()) { Text("İnternetsiz izle") }
                    Text("Telefonda ✓ İnternet gerekmez.", style = MaterialTheme.typography.bodySmall)
                }
                OdevDosyalari.indirilebilir(videoUrl) -> {
                    Text(
                        "Kotasız ağa bağlanınca telefonuna inecek${boyut?.let { " ($it)" } ?: ""}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val yuzde = homework.sizeMb?.let { KotaTercihi.yuzde(kota, it) }
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))) }
                    }) { Text("Şimdi internetten aç (mobil veri harcar${yuzde?.let { ", kotanın %$it'i" } ?: ""})") }
                }
                else -> TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))) }
                }) { Text("Bağlantıyı aç") }
            }
            Button(
                onClick = onSubmit,
                enabled = !submitted && !submitting,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(when {
                    submitted -> "Öğretmene gönderildi"
                    submitting -> "Gönderiliyor"
                    else -> "Tamamladım, öğretmene gönder"
                })
            }
        }
    }
    if (oynat) {
        VideoOynatici(OdevDosyalari.dosya(context, homework.id), onDismiss = { oynat = false }, onStarted = onOpened)
    }
}

/* Telefona inmiş videoyu internetsiz oynatır; dokununca durur/devam eder */
@Composable
private fun VideoOynatici(dosya: java.io.File, onDismiss: () -> Unit, onStarted: () -> Unit) {
    var bildirildi by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(dosya.absolutePath)
                            setOnPreparedListener { start() }
                            setOnInfoListener { _, what, _ ->
                                if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && !bildirildi) {
                                    bildirildi = true
                                    onStarted()
                                }
                                false
                            }
                            setOnClickListener { if (isPlaying) pause() else start() }
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
                Text("Videoya dokununca durur / devam eder.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Kapat") }
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
    modifier: Modifier, studentName: String, reading: RadioReading?,
    measuring: Boolean, auto: Boolean, onAuto: (Boolean) -> Unit,
    demoMode: Boolean, onDemo: (Boolean) -> Unit,
    hasCellPermission: Boolean, requestPermission: () -> Unit,
    onMeasure: () -> Unit, message: String,
    onAksamDene: () -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Bağlantını tanı", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Ödevlerin için bağlantının durumunu gör.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Şebeke: ${reading?.technology ?: if (demoMode) "Demo" else "Kullanılamıyor"}" +
                (reading?.ag?.let { if (it == "wifi") " · test Wi-Fi üzerinden" else " · test mobil veriyle" } ?: ""),
            color = MaterialTheme.colorScheme.primary
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Demo değerleri (temsili)", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Switch(checked = demoMode, onCheckedChange = onDemo, enabled = !measuring)
        }
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ÖLÇÜM SAHİBİ", style = MaterialTheme.typography.labelMedium)
                Text(studentName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        // Tezimiz: çubuk dolu olabilir ama ödev için yetmeyebilir
        val cubuk = cubukMetni(reading?.level)
        val karar = reading?.let(::kararMetni)
        if (cubuk != null || karar != null) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cubuk != null) Text("Telefonun çubuğu: $cubuk", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ödev için: ${karar ?: "hız testi yapınca görünür"}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("RSRP", reading?.rsrp?.let { "$it dBm" } ?: "Veri yok", Modifier.weight(1f))
            MetricCard("SINR", reading?.sinr?.let { "$it dB" } ?: "Veri yok", Modifier.weight(1f))
            MetricCard("RSRQ", reading?.rsrq?.let { "$it dB" } ?: "Veri yok", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("İndirme", reading?.mbps?.let { "%.1f Mbps".format(it) } ?: "—", Modifier.weight(1f))
            MetricCard("Yükleme", reading?.upMbps?.let { "%.2f Mbps".format(it) } ?: "—", Modifier.weight(1f))
            MetricCard("HTTP gecikme", reading?.pingMs?.let { "%.0f ms".format(it) } ?: "—", Modifier.weight(1f))
        }
        if (!demoMode && reading != null && (reading.sinr == null || reading.rsrq == null)) {
            Text(
                "Cihaz bazı sinyal değerlerini bildirmiyor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!hasCellPermission && !demoMode) Button(onClick = requestPermission) { Text("Hücre verisi izni ver") }
        Button(
            onClick = onMeasure, enabled = !measuring,
            shape = RoundedCornerShape(20.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            if (measuring) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (demoMode) "Ölç (demo)" else "Ölç + hız testi (~3,5 MB)", style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Otomatik mod", fontWeight = FontWeight.Bold)
                Text("Ekran açıkken her 10 saniyede sinyal; hız testi yapılmaz", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = auto, onCheckedChange = onAuto, enabled = hasCellPermission || demoMode)
        }
        OutlinedButton(onClick = onAksamDene, enabled = !demoMode, modifier = Modifier.fillMaxWidth()) {
            Text("Ödev saati ölçümünü şimdi dene")
        }
        Text(
            "Akşam 19:00–23:00 arasında denenir; Android pil tasarrufu işi geciktirebilir. Konum okunmaz. Kota seçilmediyse veya 2 GB ve altındaysa arka planda mobil hız testi yapılmaz.",
            style = MaterialTheme.typography.bodySmall
        )
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
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


@Preview(name = "Öğrenci alanı", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun StudentHomePreview() {
    StudentTheme {
        HomeScreen(Modifier, Student("Ali Eren", "AE", "7-A", "482"), emptyList(), null, {}, false,
            listOf(Homework(1, "Kesirlerle İşlemler", null, "Sadece mesaj", "7-A")), false, null,
            emptyList(), emptyList(), {}, {}, {}, {}, emptyList(), "2 GB", {}, {})
    }
}

@Preview(name = "Öğrenci girişi", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun StudentLoginPreview() { StudentTheme { LoginScreen(false, null) { _, _ -> } } }
