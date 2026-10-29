package ru.depocred.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private var pendingFile: File? = null
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = pendingFile
        pendingFile = null
        if (uri != null && file != null) lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt").use { output ->
                        checkNotNull(output) { "Не удалось открыть файл" }
                        file.inputStream().use { it.copyTo(output) }
                    }
                }
                model.notify("PDF сохранён")
            } catch (_: Exception) { model.notify("Не удалось сохранить PDF в выбранную папку") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        pendingFile = savedInstanceState?.getString("pendingPdf")?.let(::File)
        setContent { DepoCredApp(model, ::exportPdf, ::openLink, ::shareApp) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        pendingFile?.let { outState.putString("pendingPdf", it.absolutePath) }
        super.onSaveInstanceState(outState)
    }
    override fun onStop() { model.flush(); super.onStop() }
    private fun exportPdf(entry: SavedCalculation, save: Boolean) {
        model.notify("Создание PDF-отчёта…")
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { PdfReport.create(this@MainActivity, entry, model.state.settings.skin) }
                if (save) {
                    pendingFile = file
                    createDocument.launch(file.name)
                } else {
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                    startActivity(Intent.createChooser(pdfShareIntent(uri), "Отправить PDF"))
                }
            } catch (_: Exception) { model.notify("Не удалось подготовить PDF. Проверьте свободное место и повторите.") }
        }
    }
    private fun openLink(url: String) {
        val intent = if (url.startsWith("mailto:")) Intent(Intent.ACTION_SENDTO, Uri.parse(url)) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }.onFailure { model.notify("На устройстве нет приложения для открытия ссылки") }
    }
    private fun shareApp() {
        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "DepoCred — финансовый калькулятор кредитов, вкладов и лизинга. https://depocred.ru")
        }, "Поделиться приложением")) }.onFailure { model.notify("Не удалось открыть меню отправки") }
    }
}

// One MIME type, a readable content URI, ClipData, and a temporary read grant.
// No mixed text/file payload and no publicly accessible external-storage path.
fun pdfShareIntent(uri: Uri) = Intent(Intent.ACTION_SEND).apply {
    type = "application/pdf"
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newRawUri("DepoCred PDF", uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
