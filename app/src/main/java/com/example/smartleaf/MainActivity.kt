package com.example.smartleaf

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Suppress("SpellCheckingInspection", "DEPRECATION")
@SuppressLint("SetTextI18n", "NotifyDataSetChanged")
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Main Layouts
    private lateinit var topBarLayout: View
    private lateinit var bottomNavLayout: View
    private lateinit var layoutDashboard: NestedScrollView
    private lateinit var layoutScanner: View
    private lateinit var layoutRemedies: NestedScrollView
    private lateinit var layoutSettings: NestedScrollView

    // Dynamic Text Elements for Translation
    private var tvAppTitle: TextView? = null
    private var tvPlantDoctorTitle: TextView? = null
    private var tvPlantDoctorDesc: TextView? = null
    private var tvRecentDiagnoses: TextView? = null
    private var tvEncyclopediaTitle: TextView? = null
    private var tvSettingsTitle: TextView? = null
    private var tvDataMgmtTitle: TextView? = null

    // Dashboard Controls
    private lateinit var switchLanguage: SwitchCompat
    private lateinit var btnScanLeaf: Button
    private lateinit var btnGalleryDash: Button
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvNoHistory: TextView

    // Bottom Navigation
    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navRemedies: LinearLayout
    private lateinit var navSettings: LinearLayout
    private lateinit var fabScan: ImageButton

    private lateinit var btnClearHistory: Button

    // Scanner
    private lateinit var viewFinder: PreviewView
    private lateinit var cameraControls: LinearLayout
    private lateinit var scannerDashboard: NestedScrollView
    private lateinit var ivCapturedImage: ImageView
    private lateinit var btnCloseScanner: Button
    private lateinit var btnCapturePhoto: Button
    private lateinit var btnSpeak: Button
    private lateinit var btnRetake: Button
    private lateinit var tvDisease: TextView
    private lateinit var tvRemedy: TextView
    private var tvConfidence: TextView? = null
    private var btnDownload: Button? = null

    // Services & State
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var tflite: Interpreter? = null
    private var labels: List<String> = emptyList()

    // Language & Result State Tracker
    private var isTelugu = false
    private var lastDetectedDiseaseRaw = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // FIX 3: Added systemBars.top to ensure "WELCOME TO" does not overlap the phone clock/battery icons
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        // Link Global UI Components
        topBarLayout = findViewById(R.id.topBarLayout)
        bottomNavLayout = findViewById(R.id.bottomNavLayout)

        // Link Content Layouts
        layoutDashboard = findViewById(R.id.layoutDashboard)
        layoutScanner = findViewById(R.id.layoutScanner)
        layoutRemedies = findViewById(R.id.layoutRemedies)
        layoutSettings = findViewById(R.id.layoutSettings)

        // Link Translatable Texts
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvPlantDoctorTitle = findViewById(R.id.tvPlantDoctorTitle)
        tvPlantDoctorDesc = findViewById(R.id.tvPlantDoctorDesc)
        tvRecentDiagnoses = findViewById(R.id.tvRecentDiagnoses)
        tvEncyclopediaTitle = findViewById(R.id.tvEncyclopediaTitle)
        tvSettingsTitle = findViewById(R.id.tvSettingsTitle)
        tvDataMgmtTitle = findViewById(R.id.tvDataMgmtTitle)

        // Link Controls
        switchLanguage = findViewById(R.id.switchLanguage)
        btnScanLeaf = findViewById(R.id.btnScanLeaf)
        btnGalleryDash = findViewById(R.id.btnGalleryDash)
        viewFinder = findViewById(R.id.viewFinder)
        cameraControls = findViewById(R.id.cameraControls)
        scannerDashboard = findViewById(R.id.scannerDashboard)
        ivCapturedImage = findViewById(R.id.ivCapturedImage)

        btnCloseScanner = findViewById(R.id.btnCloseScanner)
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto)
        btnSpeak = findViewById(R.id.btnSpeak)
        btnRetake = findViewById(R.id.btnRetake)
        tvDisease = findViewById(R.id.tvDisease)
        tvRemedy = findViewById(R.id.tvRemedy)

        tvConfidence = findViewById(R.id.tvConfidence)
        btnDownload = findViewById(R.id.btnDownload)

        // FIX 2: Removed the ugly hyperlink styling. It will now be a clean black button with white text as defined in XML.

        rvHistory = findViewById(R.id.rvHistory)
        tvNoHistory = findViewById(R.id.tvNoHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)
        navRemedies = findViewById(R.id.navRemedies)
        navSettings = findViewById(R.id.navSettings)
        fabScan = findViewById(R.id.fabScan)

        // Setup Services
        tts = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupAIModel()
        loadHistory()

        // Language Toggle Listener
        switchLanguage.setOnCheckedChangeListener { _, isChecked ->
            isTelugu = isChecked
            updateUILanguage()
            Toast.makeText(this, if (isTelugu) "తెలుగు ఎంచుకోబడింది" else "English Selected", Toast.LENGTH_SHORT).show()
        }

        // Navigation Clicks
        btnScanLeaf.setOnClickListener { checkCameraPermissionAndOpen() }
        btnGalleryDash.setOnClickListener { pickImageLauncher.launch("image/*") }
        btnCloseScanner.setOnClickListener { showTab("HOME") }
        btnCapturePhoto.setOnClickListener { takePhoto() }
        btnRetake.setOnClickListener { openLiveCamera() }

        navHome.setOnClickListener { showTab("HOME") }
        navHistory.setOnClickListener { showTab("HISTORY") }
        navRemedies.setOnClickListener { showTab("REMEDIES") }
        navSettings.setOnClickListener { showTab("SETTINGS") }
        fabScan.setOnClickListener { checkCameraPermissionAndOpen() }

        btnClearHistory.setOnClickListener {
            val prefs = getSharedPreferences("SmartLeafHistory", Context.MODE_PRIVATE)
            val historyString = prefs.getString("history_data", "") ?: ""
            historyString.split(";;").filter { it.isNotBlank() }.forEach { item ->
                val data = item.split("|")
                if (data.size > 3 && data[3] != "none") {
                    File(data[3]).delete()
                }
            }
            prefs.edit { clear() }

            val toastMsg = if (isTelugu) "స్కాన్ చరిత్ర తొలగించబడింది!" else "Scan history cleared!"
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            loadHistory()
        }

        // VOICE READING LOGIC
        btnSpeak.setOnClickListener {
            if (!isTtsInitialized) {
                Toast.makeText(this, "Voice engine is still initializing...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val textToRead = "${tvDisease.text}. ${tvRemedy.text}"
            if (isTelugu) {
                val result = tts?.setLanguage(Locale("te", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "Telugu Voice not supported on this device. Reading in English.", Toast.LENGTH_LONG).show()
                    tts?.setLanguage(Locale.ENGLISH)
                }
            } else {
                tts?.setLanguage(Locale.ENGLISH)
            }
            tts?.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, "")
        }

        btnDownload?.setOnClickListener {
            downloadReportAsPdf(
                tvDisease.text.toString(),
                tvConfidence?.text.toString() ?: "",
                tvRemedy.text.toString()
            )
        }

        // Initialize UI translation state & Default Tab
        updateUILanguage()
        showTab("HOME")
    }

    private fun updateUILanguage() {
        if (isTelugu) {
            tvAppTitle?.text = "స్మార్ట్ లీఫ్"
            tvPlantDoctorTitle?.text = "AI క్రాప్ అనాలిసిస్"
            tvPlantDoctorDesc?.text = "వ్యాధులను గుర్తించడానికి మరియు తక్షణ చికిత్స పరిష్కారాలను పొందడానికి ఏదైనా ఆకు యొక్క ఫోటోను తీయండి."
            btnScanLeaf.text = "ఆకును స్కాన్ చేయండి"
            btnGalleryDash.text = "గ్యాలరీ నుండి అప్‌లోడ్ చేయండి"
            tvRecentDiagnoses?.text = "ఇటీవలి నిర్ధారణలు"
            tvNoHistory.text = "ఇటీవలి స్కాన్‌లు లేవు. ప్రారంభించడానికి స్కాన్ చేయండి!"

            if (tvDisease.text == "Analyzing..." || tvDisease.text == "విశ్లేషిస్తోంది...") {
                tvDisease.text = "విశ్లేషిస్తోంది..."
                tvRemedy.text = "దయచేసి వేచి ఉండండి."
                tvConfidence?.text = "..."
            } else if (lastDetectedDiseaseRaw.isNotEmpty()) {
                tvDisease.text = getTranslatedDiseaseName(lastDetectedDiseaseRaw, true)
                tvRemedy.text = getRemedyForDisease(lastDetectedDiseaseRaw, true)

                // FIX 1: Also safely translate the Status Text (Confidence view) so it doesn't get stuck in English!
                tvConfidence?.text = if (lastDetectedDiseaseRaw.contains("Healthy", ignoreCase = true)) "ఆరోగ్యకరమైనది" else "వ్యాధి కనుగొనబడింది"
            }

            btnRetake.text = "మళ్ళీ తీయండి"
            btnSpeak.text = "చదివి వినిపించు"
            btnDownload?.text = "PDF రిపోర్ట్ సేవ్ చేయండి"
            btnCloseScanner.text = "← వెనుకకు"

            updateNavText(navHome, "హోమ్")
            updateNavText(navHistory, "చరిత్ర")
            updateNavText(navRemedies, "పరిష్కారాలు")
            updateNavText(navSettings, "సెట్టింగ్‌లు")

            tvSettingsTitle?.text = "సెట్టింగ్‌లు"
            tvDataMgmtTitle?.text = "డేటా నిర్వహణ"
            btnClearHistory.text = "స్కాన్ చరిత్రను తొలగించండి"
            tvEncyclopediaTitle?.text = "ఎన్‌సైక్లోపీడియా"
        } else {
            tvAppTitle?.text = "SmartLeaf"
            tvPlantDoctorTitle?.text = "AI Crop Analysis"
            tvPlantDoctorDesc?.text = "Snap a photo of any leaf to identify diseases and get instant treatment remedies."
            btnScanLeaf.text = "Scan Leaf"
            btnGalleryDash.text = "Upload from Gallery"
            tvRecentDiagnoses?.text = "Recent Diagnoses"
            tvNoHistory.text = "No recent scans. Scan a leaf to get started!"

            if (tvDisease.text == "Analyzing..." || tvDisease.text == "విశ్లేషిస్తోంది...") {
                tvDisease.text = "Analyzing..."
                tvRemedy.text = "Please wait."
                tvConfidence?.text = "..."
            } else if (lastDetectedDiseaseRaw.isNotEmpty()) {
                tvDisease.text = getTranslatedDiseaseName(lastDetectedDiseaseRaw, false)
                tvRemedy.text = getRemedyForDisease(lastDetectedDiseaseRaw, false)

                // FIX 1: Also safely translate the Status Text (Confidence view) so it doesn't get stuck in Telugu!
                tvConfidence?.text = if (lastDetectedDiseaseRaw.contains("Healthy", ignoreCase = true)) "Healthy" else "Defect Detected"
            }

            btnRetake.text = "Retake"
            btnSpeak.text = "Read Aloud"
            btnDownload?.text = "Save PDF Report"
            btnCloseScanner.text = "← Back"

            updateNavText(navHome, "Home")
            updateNavText(navHistory, "History")
            updateNavText(navRemedies, "Remedies")
            updateNavText(navSettings, "Settings")

            tvSettingsTitle?.text = "Settings"
            tvDataMgmtTitle?.text = "Data Management"
            btnClearHistory.text = "Clear Scan History"
            tvEncyclopediaTitle?.text = "Encyclopedia"
        }
        rvHistory.adapter?.notifyDataSetChanged()
    }

    private fun updateNavText(navLayout: LinearLayout, text: String) {
        for (i in 0 until navLayout.childCount) {
            val child = navLayout.getChildAt(i)
            if (child is TextView) child.text = text
        }
    }

    private fun showTab(tab: String) {
        tts?.stop()

        topBarLayout.visibility = View.VISIBLE
        bottomNavLayout.visibility = View.VISIBLE

        layoutDashboard.visibility = View.GONE
        layoutScanner.visibility = View.GONE
        layoutRemedies.visibility = View.GONE
        layoutSettings.visibility = View.GONE
        cameraProvider?.unbindAll()

        val grey = Color.parseColor("#888888")
        val green = Color.parseColor("#2E7D32")

        val resetNav = { nav: LinearLayout ->
            for (i in 0 until nav.childCount) {
                val child = nav.getChildAt(i)
                if (child is TextView) {
                    child.setTextColor(grey)
                    child.setTypeface(null, Typeface.NORMAL)
                } else if (child is ImageView) {
                    child.setColorFilter(grey)
                }
            }
        }

        val highlightNav = { nav: LinearLayout ->
            for (i in 0 until nav.childCount) {
                val child = nav.getChildAt(i)
                if (child is TextView) {
                    child.setTextColor(green)
                    child.setTypeface(null, Typeface.BOLD)
                } else if (child is ImageView) {
                    child.setColorFilter(green)
                }
            }
        }

        resetNav(navHome)
        resetNav(navHistory)
        resetNav(navRemedies)
        resetNav(navSettings)

        when (tab) {
            "HOME" -> {
                layoutDashboard.visibility = View.VISIBLE
                highlightNav(navHome)
                loadHistory()
            }
            "HISTORY" -> {
                layoutDashboard.visibility = View.VISIBLE
                highlightNav(navHistory)
                loadHistory()
                layoutDashboard.post { layoutDashboard.smoothScrollTo(0, rvHistory.top) }
            }
            "REMEDIES" -> {
                layoutRemedies.visibility = View.VISIBLE
                highlightNav(navRemedies)
            }
            "SETTINGS" -> {
                layoutSettings.visibility = View.VISIBLE
                highlightNav(navSettings)
            }
        }
    }

    private fun saveToHistory(rawDiseaseName: String, confidenceText: String, bitmap: Bitmap) {
        val prefs = getSharedPreferences("SmartLeafHistory", Context.MODE_PRIVATE)
        val currentHistory = prefs.getString("history_data", "") ?: ""
        val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        var imagePath = "none"
        try {
            val file = File(applicationContext.filesDir, "hist_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.flush()
            out.close()
            imagePath = file.absolutePath
        } catch (e: Exception) {
            Log.e("SmartLeaf", "Failed to save thumbnail", e)
        }
        val newItem = "$rawDiseaseName|$confidenceText|$date|$imagePath"

        val historyItems = currentHistory.split(";;").filter { it.isNotBlank() }.toMutableList()
        historyItems.add(0, newItem)

        while (historyItems.size > 10) {
            val oldestItem = historyItems.removeAt(historyItems.size - 1)
            val oldData = oldestItem.split("|")
            if (oldData.size > 3 && oldData[3] != "none") {
                File(oldData[3]).delete()
            }
        }

        prefs.edit { putString("history_data", historyItems.joinToString(";;")) }
        loadHistory()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("SmartLeafHistory", Context.MODE_PRIVATE)
        val historyString = prefs.getString("history_data", "") ?: ""
        if (historyString.isEmpty()) {
            tvNoHistory.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
            return
        }
        tvNoHistory.visibility = View.GONE
        rvHistory.visibility = View.VISIBLE
        rvHistory.layoutManager = LinearLayoutManager(this)

        val historyItems = historyString.split(";;").filter { it.isNotBlank() }.take(5)
        rvHistory.adapter = HistoryAdapter(historyItems)
    }

    private fun showHistoryResult(rawDisease: String, confText: String, imagePath: String) {
        tts?.stop()

        runOnUiThread {
            topBarLayout.visibility = View.VISIBLE
            bottomNavLayout.visibility = View.GONE

            layoutDashboard.visibility = View.GONE
            layoutRemedies.visibility = View.GONE
            layoutSettings.visibility = View.GONE

            layoutScanner.visibility = View.VISIBLE
            viewFinder.visibility = View.GONE
            cameraControls.visibility = View.GONE
            scannerDashboard.visibility = View.VISIBLE
            scannerDashboard.scrollTo(0, 0)

            if (imagePath != "none" && File(imagePath).exists()) {
                ivCapturedImage.setImageURI(Uri.fromFile(File(imagePath)))
            } else {
                ivCapturedImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            lastDetectedDiseaseRaw = rawDisease

            tvDisease.text = getTranslatedDiseaseName(rawDisease, isTelugu)
            if (rawDisease.contains("Healthy", ignoreCase = true)) {
                tvDisease.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                tvDisease.setTextColor(Color.parseColor("#FF5252"))
            }

            tvRemedy.text = getRemedyForDisease(rawDisease, isTelugu)

            // Sync status text to correctly match current language toggle
            tvConfidence?.text = if (isTelugu) {
                if (rawDisease.contains("Healthy", true)) "ఆరోగ్యకరమైనది" else "వ్యాధి కనుగొనబడింది"
            } else {
                if (rawDisease.contains("Healthy", true)) "Healthy" else "Defect Detected"
            }

            btnDownload?.visibility = View.VISIBLE
        }
    }

    private fun downloadReportAsPdf(disease: String, confidence: String, remedy: String) {
        val pdfDocument = PdfDocument()

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            textSize = 28f
            isFakeBoldText = true
            color = Color.parseColor("#1B5E20") // Dark Green
        }

        val headingPaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
            color = Color.BLACK
        }

        val textPaint = TextPaint().apply {
            textSize = 16f
            color = Color.parseColor("#333333") // Dark Gray
        }

        val diseasePaint = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
            color = if (lastDetectedDiseaseRaw.contains("Healthy", true)) {
                Color.parseColor("#2E7D32") // Green
            } else {
                Color.parseColor("#D32F2F") // Red
            }
        }

        var yPosition = 80f
        val leftMargin = 50f

        val reportTitle = if (isTelugu) "స్మార్ట్ లీఫ్ పంట నివేదిక" else "SmartLeaf Crop Report"
        canvas.drawText(reportTitle, leftMargin, yPosition, titlePaint)

        yPosition += 30f
        val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val dateLabel = if (isTelugu) "తేదీ: $timestamp" else "Date: $timestamp"
        canvas.drawText(dateLabel, leftMargin, yPosition, textPaint)

        yPosition += 30f
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f }
        canvas.drawLine(leftMargin, yPosition, pageInfo.pageWidth - leftMargin, yPosition, linePaint)

        yPosition += 50f
        val diagLabel = if (isTelugu) "రోగ నిర్ధారణ:" else "DIAGNOSIS:"
        canvas.drawText(diagLabel, leftMargin, yPosition, headingPaint)
        yPosition += 30f
        canvas.drawText(disease, leftMargin, yPosition, diseasePaint)

        yPosition += 50f
        val confLabel = if (isTelugu) "స్థితి:" else "STATUS:"
        canvas.drawText(confLabel, leftMargin, yPosition, headingPaint)
        yPosition += 30f
        canvas.drawText(confidence, leftMargin, yPosition, textPaint)

        yPosition += 50f
        val remLabel = if (isTelugu) "పరిష్కారం (రెమెడీ):" else "TREATMENT REMEDY:"
        canvas.drawText(remLabel, leftMargin, yPosition, headingPaint)
        yPosition += 30f

        val textWidth = (pageInfo.pageWidth - (leftMargin * 2)).toInt()
        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(remedy, 0, remedy.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false).build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(remedy, textPaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
        }

        canvas.save()
        canvas.translate(leftMargin, yPosition)
        staticLayout.draw(canvas)
        canvas.restore()

        pdfDocument.finishPage(page)

        val filename = "SmartLeaf_Report_${System.currentTimeMillis()}.pdf"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os -> pdfDocument.writeTo(os) }
                }
                val toastMsg = if (isTelugu) "PDF నివేదిక సేవ్ చేయబడింది!" else "PDF Report saved to Downloads!"
                Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()
            } else {
                val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), filename)
                FileOutputStream(file).use { os -> pdfDocument.writeTo(os) }
                val toastMsg = if (isTelugu) "PDF నివేదిక సేవ్ చేయబడింది (Documents)!" else "PDF Report saved to App Documents!"
                Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()
            }
            pdfDocument.close()
        } catch (e: Exception) {
            pdfDocument.close()
            Toast.makeText(this, "Failed to save PDF.", Toast.LENGTH_SHORT).show()
            Log.e("SmartLeaf", "PDF Error", e)
        }
    }

    private fun openLiveCamera() {
        runOnUiThread {
            topBarLayout.visibility = View.VISIBLE
            bottomNavLayout.visibility = View.GONE

            layoutDashboard.visibility = View.GONE
            layoutRemedies.visibility = View.GONE
            layoutSettings.visibility = View.GONE

            layoutScanner.visibility = View.VISIBLE
            viewFinder.visibility = View.VISIBLE
            cameraControls.visibility = View.VISIBLE
            scannerDashboard.visibility = View.GONE
        }
        startCamera()
    }

    private fun showAnalysisResult(bitmap: Bitmap) {
        runOnUiThread {
            topBarLayout.visibility = View.VISIBLE
            bottomNavLayout.visibility = View.GONE

            layoutDashboard.visibility = View.GONE
            layoutScanner.visibility = View.VISIBLE
            viewFinder.visibility = View.GONE
            cameraControls.visibility = View.GONE

            ivCapturedImage.setImageBitmap(bitmap)
            scannerDashboard.visibility = View.VISIBLE
            scannerDashboard.scrollTo(0, 0)

            tvDisease.text = if (isTelugu) "విశ్లేషిస్తోంది..." else "Analyzing..."
            tvDisease.setTextColor(Color.WHITE)
            tvRemedy.text = if (isTelugu) "దయచేసి వేచి ఉండండి." else "Please wait."
            tvConfidence?.text = "..."

            btnDownload?.visibility = View.GONE
        }
        classifyImage(bitmap)
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                cameraProvider?.unbindAll()
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                showAnalysisResult(bitmap.copy(Bitmap.Config.ARGB_8888, true))
            } catch (_: Exception) {
                Toast.makeText(this, "Failed to load image securely", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openLiveCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openLiveCamera()
        else Toast.makeText(this, "Camera permission required.", Toast.LENGTH_LONG).show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bitmap = imageProxy.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
                imageProxy.close()
                cameraProvider?.unbindAll()
                showAnalysisResult(bitmap)
            }
            override fun onError(exception: ImageCaptureException) {}
        })
    }

    private fun setupAIModel() {
        try {
            labels = assets.open("labels.txt").bufferedReader().readLines()
            val fileDescriptor = assets.openFd("model.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            tflite = Interpreter(fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength), Interpreter.Options())
        } catch (e: Exception) {
            Log.e("SmartLeaf", "Failed to load Primary Model", e)
            try {
                val bytes = assets.open("model.tflite").readBytes()
                val buffer = ByteBuffer.allocateDirect(bytes.size).apply { order(ByteOrder.nativeOrder()); put(bytes); rewind() }
                tflite = Interpreter(buffer, Interpreter.Options())
            } catch (fallbackEx: Exception) {
                Log.e("SmartLeaf", "Failed to load Fallback Model", fallbackEx)
            }
        }
    }

    private fun classifyImage(bitmap: Bitmap) {
        cameraExecutor.execute {
            val interpreter = tflite

            if (interpreter == null || labels.isEmpty()) {
                runOnUiThread {
                    tvDisease.text = if (isTelugu) "నమూనా లోపం" else "Model Error"
                    tvDisease.setTextColor(Color.parseColor("#D32F2F"))
                    tvConfidence?.text = "Failed"
                    tvRemedy.text = if (isTelugu) "AI మోడల్ ప్రారంభించబడలేదు. దయచేసి యాప్‌ని పునఃప్రారంభించండి." else "AI Model failed to initialize. Please restart the app."
                    btnDownload?.visibility = View.GONE
                }
                return@execute
            }

            try {
                val inputTensor = interpreter.getInputTensor(0)
                val shape = inputTensor.shape()
                val imageSizeY = shape[1]; val imageSizeX = shape[2]
                val dataType = inputTensor.dataType()

                val resizedBitmap = bitmap.scale(imageSizeX, imageSizeY, true)
                val bytesPerChannel = if (dataType == org.tensorflow.lite.DataType.UINT8) 1 else 4
                val imgData = ByteBuffer.allocateDirect(1 * imageSizeX * imageSizeY * 3 * bytesPerChannel).apply { order(ByteOrder.nativeOrder()) }

                val intValues = IntArray(imageSizeX * imageSizeY)
                resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

                var pixel = 0
                repeat(imageSizeX * imageSizeY) {
                    val valInt = intValues[pixel++]
                    if (dataType == org.tensorflow.lite.DataType.UINT8) {
                        imgData.put((valInt shr 16 and 0xFF).toByte())
                        imgData.put((valInt shr 8 and 0xFF).toByte())
                        imgData.put((valInt and 0xFF).toByte())
                    } else {
                        imgData.putFloat((valInt shr 16 and 0xFF).toFloat())
                        imgData.putFloat((valInt shr 8 and 0xFF).toFloat())
                        imgData.putFloat((valInt and 0xFF).toFloat())
                    }
                }

                val outDataType = interpreter.getOutputTensor(0).dataType()
                var maxIdx = 0; var maxConfidence = 0f

                if (outDataType == org.tensorflow.lite.DataType.UINT8) {
                    val outputBuffer = Array(1) { ByteArray(labels.size) }
                    interpreter.run(imgData, outputBuffer)
                    for (i in outputBuffer[0].indices) {
                        val confFloat = (outputBuffer[0][i].toInt() and 0xFF) / 255.0f
                        if (confFloat > maxConfidence) { maxConfidence = confFloat; maxIdx = i }
                    }
                } else {
                    val outputBuffer = Array(1) { FloatArray(labels.size) }
                    interpreter.run(imgData, outputBuffer)
                    for (i in outputBuffer[0].indices) {
                        if (outputBuffer[0][i] > maxConfidence) { maxConfidence = outputBuffer[0][i]; maxIdx = i }
                    }
                }

                runOnUiThread {
                    var rawName = labels[maxIdx].trim()
                    if (rawName.matches(Regex("^\\d+\\s+.*"))) rawName = rawName.substringAfter(" ").trim()

                    val confPercent = (maxConfidence * 100).toInt()

                    if (confPercent < 95) {
                        tvDisease.text = if (isTelugu) "గుర్తించబడలేదు (Unknown)" else "Unrecognized Object"
                        tvDisease.setTextColor(Color.parseColor("#FFA000"))

                        tvConfidence?.text = if (isTelugu) "స్కాన్ విఫలమైంది" else "Scan Rejected"
                        tvConfidence?.setTextColor(Color.parseColor("#D32F2F"))

                        tvRemedy.text = if (isTelugu)
                            "ఈ చిత్రం స్పష్టంగా గుర్తించబడలేదు. ఇది అస్పష్టంగా ఉండవచ్చు లేదా AI కి తెలియని మొక్క కావచ్చు (టొమాటో, బంగాళాదుంప, పెప్పర్ బెల్ మాత్రమే సపోర్ట్ చేస్తుంది). దయచేసి స్పష్టమైన ఫోటో తీయండి."
                        else
                            "The image could not be clearly recognized. It might be blurry, or it is a plant the AI wasn't trained on (only supports Potato, Tomato, Bell Pepper). Please retake a clear photo."

                        viewFinder.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        btnDownload?.visibility = View.GONE

                    } else {
                        lastDetectedDiseaseRaw = rawName
                        val translatedName = getTranslatedDiseaseName(rawName, isTelugu)
                        tvDisease.text = translatedName

                        if (rawName.contains("Healthy", ignoreCase = true)) {
                            tvDisease.setTextColor(Color.parseColor("#4CAF50"))
                        } else {
                            tvDisease.setTextColor(Color.parseColor("#FF5252"))
                        }

                        val disclaimer = if (isTelugu) "\n\n(గమనిక: ఈ నిర్ధారణ స్కాన్ చేయబడిన ఆకు బంగాళాదుంప, టొమాటో లేదా పెప్పర్ బెల్ అని ఊహిస్తుంది.)"
                        else "\n\n(Note: This diagnosis assumes the scanned leaf is a Potato, Tomato, or Bell Pepper.)"

                        tvRemedy.text = getRemedyForDisease(rawName, isTelugu) + disclaimer

                        val statusText = if (rawName.contains("Healthy", ignoreCase = true)) {
                            if (isTelugu) "ఆరోగ్యకరమైనది" else "Healthy"
                        } else {
                            if (isTelugu) "వ్యాధి కనుగొనబడింది" else "Defect Detected"
                        }

                        tvConfidence?.text = statusText
                        tvConfidence?.setTextColor(Color.parseColor("#1B5E20"))

                        viewFinder.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        btnDownload?.visibility = View.VISIBLE

                        saveToHistory(rawName, statusText, bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e("SmartLeaf", "Inference error", e)
                runOnUiThread {
                    tvDisease.text = "Inference Failed"
                    tvRemedy.text = "There was a mathematical error while analyzing the image."
                }
            }
        } // End of cameraExecutor.execute block
    }

    private fun getTranslatedDiseaseName(rawName: String, isTelugu: Boolean): String {
        val englishName = rawName.replace("_", " ")
        if (!isTelugu) return englishName

        return when (rawName) {
            "Pepperbell_Bacterial_Spot" -> "పెప్పర్ బెల్ బాక్టీరియల్ స్పాట్"
            "Pepperbell__Healthy" -> "పెప్పర్ బెల్ (ఆరోగ్యకరమైనది)"
            "Potato_Early_Blight" -> "బంగాళాదుంప ఎర్లీ బ్లైట్"
            "Potato_Healthy" -> "బంగాళాదుంప (ఆరోగ్యకరమైనది)"
            "Potato_Late_Blight" -> "బంగాళాదుంప లేట్ బ్లైట్"
            "Tomato_Early_Blight" -> "టొమాటో ఎర్లీ బ్లైట్"
            "Tomato_Healthy" -> "టొమాటో (ఆరోగ్యకరమైనది)"
            "Tomato_Late_Blight" -> "టొమాటో లేట్ బ్లైట్"
            else -> englishName
        }
    }

    private fun getRemedyForDisease(diseaseName: String, isTelugu: Boolean): String {
        if (isTelugu) {
            return when (diseaseName) {
                "Pepperbell_Bacterial_Spot" -> "1. వ్యాధిగ్రస్తులైన ఆకులను తీసివేసి నాశనం చేయండి.\n2. రాగి ఆధారిత (Copper-based) బాక్టీరిసైడ్లను వెంటనే పిచికారీ చేయండి.\n3. ఆకులు తడిసిపోకుండా ఉండటానికి పైనుండి నీరు పోయకండి.\n4. గాలి బాగా ఆడేలా మొక్కల మధ్య దూరం పాటించండి."
                "Pepperbell__Healthy" -> "మీ పెప్పర్ బెల్ మొక్క సంపూర్ణ ఆరోగ్యంతో ఉంది!\n\nక్రమం తప్పకుండా నీరు పోయండి, పూర్తి సూర్యరశ్మి ఉండేలా చూసుకోండి మరియు ప్రతి 2-3 వారాలకు ఒకసారి తగినంత ఎరువులు వేయండి."
                "Potato_Early_Blight" -> "1. నేల నుండి వచ్చే ఫంగస్ సోకకుండా కింది ఆకులను కత్తిరించండి.\n2. సేంద్రియ రాగి శిలీంద్ర సంహారిణిని (fungicide) వాడండి.\n3. పంట మార్పిడి పద్ధతులను పాటించండి (ఒకే చోట మళ్ళీ అదే పంట వేయకండి)."
                "Potato_Healthy" -> "మీ బంగాళాదుంప మొక్క అద్భుతంగా ఉంది!\n\nనేలలో నీరు నిలువకుండా చూసుకోండి, లోతుగా నీరు పోయండి మరియు బంగాళాదుంప పురుగుల వంటి తెగుళ్ల కోసం ఎప్పటికప్పుడు గమనిస్తూ ఉండండి."
                "Potato_Late_Blight" -> "అత్యవసర చర్య అవసరం:\n1. తీవ్రంగా వ్యాధి సోకిన మొక్కలను వెంటనే పీకేసి నాశనం చేయండి.\n2. చుట్టుపక్కల ఉన్న ఆరోగ్యకరమైన మొక్కలను రక్షించడానికి క్లోరోథలోనిల్ (Chlorothalonil) పిచికారీ చేయండి.\n3. ఆకులను ఎప్పుడూ పొడిగా ఉంచండి."
                "Tomato_Early_Blight" -> "1. గాలి ప్రసరణ కోసం కింది ఆకులను తొలగించండి.\n2. రాగి శిలీంద్ర సంహారిణిని వాడండి.\n3. మట్టి పైకి ఎగరకుండా మొక్కల అడుగుభాగంలో మల్చ్ (Mulch) వేయండి.\n4. ఆకులపై కాకుండా మొక్క మొదట్లో మాత్రమే నీరు పోయండి."
                "Tomato_Healthy" -> "మీ టొమాటో మొక్క బలంగా పెరుగుతోంది!\n\nఇలాగే కొనసాగించడానికి క్రమం తప్పకుండా తగినంత నీరు అందించండి మరియు టొమాటోలకు సరిపోయే ఎరువులను వాడండి."
                "Tomato_Late_Blight" -> "అత్యవసర చర్య అవసరం (అత్యంత అంటువ్యాధి):\n1. వ్యాధి సోకిన మొక్కలను వెంటనే పీకేసి నాశనం చేయండి.\n2. సమీపంలోని పంటలను రక్షించడానికి వెంటనే శిలీంద్ర సంహారిణి పిచికారీ చేయండి.\n3. ఆకులు తడిగా ఉన్నప్పుడు తోటలో పనిచేయకండి."
                else -> "గుర్తించబడింది: $diseaseName. దయచేసి నిర్దిష్ట చికిత్స కోసం స్థానిక వ్యవసాయ నిపుణుడిని సంప్రదించండి."
            }
        } else {
            return when (diseaseName) {
                "Pepperbell_Bacterial_Spot" -> "1. Remove and safely destroy all infected leaves.\n2. Spray copper-based bactericides immediately to halt spreading.\n3. Avoid overhead watering to keep leaves dry.\n4. Ensure good air circulation between plants."
                "Pepperbell__Healthy" -> "Your Bell Pepper plant is perfectly healthy!\n\nKeep up the good work. Maintain a regular watering schedule, ensure full sunlight, and fertilize every 2-3 weeks."
                "Potato_Early_Blight" -> "1. Prune the lowest leaves to prevent soil-borne spores from splashing up.\n2. Apply an organic copper fungicide.\n3. Practice crop rotation (do not plant nightshades in the same spot for 2-3 years)."
                "Potato_Healthy" -> "Your Potato plant is thriving!\n\nKeep the soil well-drained, water deeply but infrequently, and monitor regularly for any pests like potato beetles."
                "Potato_Late_Blight" -> "URGENT ACTION REQUIRED:\n1. Destroy severely infected plants immediately (do not compost).\n2. Apply a protective fungicide like Chlorothalonil to surrounding healthy plants.\n3. Keep foliage completely dry."
                "Tomato_Early_Blight" -> "1. Remove affected lower leaves to increase airflow.\n2. Apply a copper or biological fungicide.\n3. Add mulch to the base of the plant to prevent soil splashing.\n4. Water at the base of the stem, not on the leaves."
                "Tomato_Healthy" -> "Your Tomato plant is in excellent condition!\n\nTo maintain this, provide consistent watering to prevent blossom-end rot, and use a high-quality, tomato-specific fertilizer."
                "Tomato_Late_Blight" -> "URGENT ACTION REQUIRED (Highly Contagious):\n1. Pull up and completely destroy infected plants immediately.\n2. Apply fungicides to nearby healthy plants.\n3. Avoid working in the garden when the leaves are wet."
                else -> "Detected: $diseaseName. Please consult a local agricultural expert for specific treatment."
            }
        }
    }

    override fun onPause() {
        super.onPause()
        tts?.stop()
    }

    override fun onBackPressed() {
        tts?.stop()

        if (layoutScanner.visibility == View.VISIBLE) {
            showTab("HOME")
        } else if (layoutRemedies.visibility == View.VISIBLE || layoutSettings.visibility == View.VISIBLE) {
            showTab("HOME")
        } else {
            super.onBackPressed()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts?.language = Locale.ENGLISH
        } else {
            isTtsInitialized = false
            Log.e("SmartLeaf", "TTS Initialization Failed!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        cameraExecutor.shutdown()
        tflite?.close()
    }

    inner class HistoryAdapter(private val historyList: List<String>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivHistoryThumb: ImageView = view.findViewById(R.id.ivHistoryThumb)
            val tvHistDisease: TextView = view.findViewById(R.id.tvHistDisease)
            val tvHistDate: TextView = view.findViewById(R.id.tvHistDate)
            val cvBadge: androidx.cardview.widget.CardView = view.findViewById(R.id.cvBadge)
            val tvHistBadge: TextView = view.findViewById(R.id.tvHistBadge)
            val tvHistConf: TextView = view.findViewById(R.id.tvHistConf)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val data = historyList[position].split("|")
            if (data.size >= 3) {
                val rawDisease = data[0]
                val confRaw = data[1]
                val date = data[2]
                val imagePath = if (data.size > 3) data[3] else "none"

                holder.tvHistDisease.text = getTranslatedDiseaseName(rawDisease, isTelugu)
                holder.tvHistDate.text = date

                holder.tvHistConf.text = confRaw
                holder.tvHistConf.visibility = View.GONE

                if (rawDisease.contains("Healthy", true)) {
                    holder.tvHistBadge.text = if (isTelugu) "ఆరోగ్యం" else "Healthy"
                    holder.tvHistBadge.setTextColor(Color.parseColor("#1B5E20"))
                    holder.cvBadge.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
                } else {
                    holder.tvHistBadge.text = if (isTelugu) "చర్య అవసరం" else "Action Req."
                    holder.tvHistBadge.setTextColor(Color.parseColor("#B71C1C"))
                    holder.cvBadge.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
                }

                if (imagePath != "none" && File(imagePath).exists()) {
                    holder.ivHistoryThumb.setImageURI(Uri.fromFile(File(imagePath)))
                } else {
                    holder.ivHistoryThumb.setImageResource(android.R.drawable.ic_menu_gallery)
                }

                holder.itemView.setOnClickListener {
                    showHistoryResult(rawDisease, confRaw, imagePath)
                }
            }
        }
        override fun getItemCount() = historyList.size
    }
}