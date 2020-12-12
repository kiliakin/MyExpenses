package org.totschnig.tesseract

import Catalano.Imaging.FastBitmap
import Catalano.Imaging.Filters.BradleyLocalThreshold
import Catalano.Imaging.IApplyInPlace
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.util.getTesseractLanguageDisplayName
import org.totschnig.ocr.Element
import org.totschnig.ocr.Line
import org.totschnig.ocr.TesseractEngine
import org.totschnig.ocr.Text
import org.totschnig.ocr.TextBlock
import timber.log.Timber
import java.io.File

const val TESSERACT_DOWNLOAD_FOLDER = "tesseract4/fast/"

@Keep
object Engine : TesseractEngine {
    var timer: Long = 0
    fun initialize() {
        System.loadLibrary("jpeg")
        System.loadLibrary("png")
        System.loadLibrary("leptonica")
        System.loadLibrary("tesseract")
    }

    private fun language(context: Context, prefHandler: PrefHandler) = prefHandler.getString(PrefKey.TESSERACT_LANGUAGE, "eng")!!

    override fun tessDataExists(context: Context, prefHandler: PrefHandler) =
            File(context.getExternalFilesDir(null), filePath(language(context, prefHandler))).exists()

    override fun offerTessDataDownload(baseActivity: BaseActivity) {
        val language = language(baseActivity, baseActivity.prefHandler)
        if (language != baseActivity.downloadPending) {
            ConfirmationDialogFragment.newInstance(Bundle().apply {
                putInt(ConfirmationDialogFragment.KEY_TITLE, R.string.button_download)
                putString(ConfirmationDialogFragment.KEY_MESSAGE,
                        baseActivity.getString(R.string.tesseract_download_confirmation,
                                getTesseractLanguageDisplayName(baseActivity, language)))
                putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE, R.id.TESSERACT_DOWNLOAD_COMMAND)
            }).show(baseActivity.supportFragmentManager, "DOWNLOAD_TESSDATA")
        }
    }

    fun filePath(language: String) = "${TESSERACT_DOWNLOAD_FOLDER}tessdata/%s.traineddata".format(language)

    private fun fileName(language: String) = "%s.traineddata".format(language)

    override fun downloadTessData(context: Context, prefHandler: PrefHandler): String {
        val language = language(context, prefHandler)
        val uri = Uri.parse("https://github.com/tesseract-ocr/tessdata_fast/raw/4.0.0/%s".format(fileName(language)))
        ContextCompat.getSystemService(context, DownloadManager::class.java)?.enqueue(DownloadManager.Request(uri)
                .setTitle(context.getString(R.string.pref_tesseract_language_title))
                .setDescription(language)
                .setDestinationInExternalFilesDir(context, null, filePath(language)))
        return language
    }

    override suspend fun run(file: File, context: Context, prefHandler: PrefHandler): Text =
            withContext(Dispatchers.Default) {
                initialize()
                with(TessBaseAPI()) {
                    timer = System.currentTimeMillis()
                    if (!init(File(context.getExternalFilesDir(null), TESSERACT_DOWNLOAD_FOLDER).path, language(context, prefHandler))) {
                        throw IllegalStateException("Could not init Tesseract")
                    }
                    timing("Init")
                    setVariable("tessedit_do_invert", TessBaseAPI.VAR_FALSE)
                    setVariable("load_system_dawg", TessBaseAPI.VAR_FALSE)
                    setVariable("load_freq_dawg", TessBaseAPI.VAR_FALSE)
                    pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
                    var bitmap = with(FastBitmap(file.path)) {
                        val g: IApplyInPlace = BradleyLocalThreshold()
                        g.applyInPlace(this)
                        toBitmap()
                    }
                    /*if (scale < 10) {
                        bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale / 10), (bitmap.height * scale / 10), true)
                    }*/
                    setImage(bitmap)
                    timing("SetImage")
                    utF8Text
                    timing("utF8Text")
                    val lines = mutableListOf<Line>()
                    with(resultIterator) {
                        begin()
                        do {
                            val lineText = getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                            val lineBoundingRect = getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                            val elements = mutableListOf<Element>()
                            do {
                                val wordText = getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                                val wordBoundingRect = getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                                elements.add(Element(wordText, wordBoundingRect))
                            } while (!isAtFinalElement(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE, TessBaseAPI.PageIteratorLevel.RIL_WORD) && next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                            lines.add(Line(lineText, lineBoundingRect, elements))
                        } while (next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                        delete()
                    }
                    timing("resultIterator")
                    end()
                    timing("end")
                    Text(listOf(TextBlock(lines)))
                }
            }

    fun timing(step: String) {
        val delta = System.currentTimeMillis() - timer
        Timber.i("Timing (%s): %d", step, delta)
        timer = System.currentTimeMillis()
    }
}
