package io.xenxo.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Stack
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Navegador Web Autónomo de Alto Rendimiento para Wear OS (368×448 AMOLED).
 *
 * Características:
 *  - Compatible 100% con Wear OS 2 (sin requerir android.webkit.WebView que Google eliminó en wearables).
 *  - Motor HTTP/HTTPS multihilo con User-Agent Chromium Mobile.
 *  - Parseo y maquetación de HTML completo con texto enriquecido, enlaces interactivos e imágenes.
 *  - Fondo negro puro AMOLED (#000000).
 *  - Historial de navegación (Atrás / Adelante / Recargar).
 *  - Marcadores rápidos (Google, DuckDuckGo, Wikipedia, El Tiempo wttr.in, NeverSSL para portales cautivos).
 *  - Soporte de scroll mediante la corona rotatoria física (onGenericMotionEvent).
 */
class BrowserActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var mainCol: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var txtUrl: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnForward: TextView
    private lateinit var btnReload: TextView
    private lateinit var contentView: TextView
    private lateinit var webScrollView: ScrollView
    private lateinit var errorLayout: LinearLayout
    private lateinit var txtError: TextView
    private lateinit var quickBar: HorizontalScrollView

    private var currentUrl: String = DEFAULT_HOME
    private val backStack = Stack<String>()
    private val forwardStack = Stack<String>()

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    companion object {
        const val DEFAULT_HOME = "https://html.duckduckgo.com/html/"
        const val GOOGLE_SEARCH = "https://www.google.com/search?q="
        const val DUCK_SEARCH = "https://html.duckduckgo.com/html/?q="
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 9; Wear OS) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val d = resources.displayMetrics.density

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Barra superior de navegación compacta (38 dp)
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF141416.toInt())
            setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (38 * d).toInt()
            )
        }

        btnBack = createNavBtn("‹", 22f, d) {
            vibrateTick()
            goBack()
        }
        navBar.addView(btnBack)

        btnForward = createNavBtn("›", 22f, d) {
            vibrateTick()
            goForward()
        }
        navBar.addView(btnForward)

        txtUrl = TextView(this).apply {
            text = "Buscar o URL..."
            textSize = 11f
            setTextColor(0xFFE5E5EA.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * d
                setColor(0xFF2C2C2E.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(0, (28 * d).toInt(), 1f).apply {
                marginStart = (4 * d).toInt()
                marginEnd = (4 * d).toInt()
            }
            setOnClickListener {
                vibrateTick()
                showUrlInputDialog()
            }
        }
        navBar.addView(txtUrl)

        btnReload = createNavBtn("↻", 16f, d) {
            vibrateTick()
            loadUrl(currentUrl, addToHistory = false)
        }
        navBar.addView(btnReload)

        val btnMenu = createNavBtn("☰", 16f, d) {
            vibrateTick()
            showBookmarksDialog()
        }
        navBar.addView(btnMenu)

        mainCol.addView(navBar)

        // 2. Barra de progreso horizontal
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (3 * d).toInt()
            )
        }
        mainCol.addView(progressBar)

        // 3. Barra horizontal de accesos rápidos
        quickBar = createQuickBar(d)
        mainCol.addView(quickBar)

        // 4. Área de contenido web scrollable
        webScrollView = object : ScrollView(this) {
            override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    val delta = -ev.getAxisValue(MotionEvent.AXIS_SCROLL) * (60 * d)
                    smoothScrollBy(0, delta.toInt())
                    vibrateTick()
                    return true
                }
                return super.onGenericMotionEvent(ev)
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (40 * d).toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        contentView = TextView(this).apply {
            setTextColor(0xFFE5E5EA.toInt())
            textSize = 13f
            setLineSpacing(4 * d, 1f)
            movementMethod = LinkMovementMethod.getInstance()
            setLinkTextColor(0xFF00E5FF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollContent.addView(contentView)

        errorLayout = createErrorView(d)
        scrollContent.addView(errorLayout)

        webScrollView.addView(scrollContent)
        mainCol.addView(webScrollView)

        root.addView(mainCol)
        setContentView(root)

        val targetUrl = intent?.data?.toString() ?: DEFAULT_HOME
        loadUrl(targetUrl, addToHistory = true)
    }

    private fun createNavBtn(lbl: String, sz: Float, d: Float, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = lbl
            textSize = sz
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
            setOnClickListener { onClick() }
        }
    }

    private fun createQuickBar(d: Float): HorizontalScrollView {
        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF0A0A0C.toInt())
            setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun addPill(name: String, url: String) {
            val pill = TextView(this).apply {
                text = name
                textSize = 11f
                setTextColor(Color.WHITE)
                setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12 * d
                    setColor(0xFF242426.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (6 * d).toInt()
                }
                setOnClickListener {
                    vibrateTick()
                    loadUrl(url, addToHistory = true)
                }
            }
            row.addView(pill)
        }

        addPill("🔍 Google", "https://www.google.com")
        addPill("🦆 DuckDuckGo", "https://html.duckduckgo.com/html/")
        addPill("📖 Wikipedia", "https://es.m.wikipedia.org")
        addPill("🌦️ Clima", "https://wttr.in/Madrid?format=3")
        addPill("📶 NeverSSL (Portal)", "http://neverssl.com")

        hScroll.addView(row)
        return hScroll
    }

    private fun createErrorView(d: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding((16 * d).toInt(), (24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val icon = TextView(context).apply {
                text = "⚠️"
                textSize = 32f
                gravity = Gravity.CENTER
            }
            addView(icon)

            txtError = TextView(context).apply {
                text = "Error al conectar con la página"
                textSize = 12f
                setTextColor(0xFFFF453A.toInt())
                gravity = Gravity.CENTER
                setPadding(0, (8 * d).toInt(), 0, (12 * d).toInt())
            }
            addView(txtError)

            val btnRetry = Button(context).apply {
                text = "Reintentar"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16 * d
                    setColor(0xFF007AFF.toInt())
                }
                setOnClickListener {
                    vibrateTick()
                    loadUrl(currentUrl, addToHistory = false)
                }
            }
            addView(btnRetry)
        }
    }

    fun loadUrl(rawUrl: String, addToHistory: Boolean = true) {
        var url = rawUrl.trim()
        if (url.isBlank()) return

        // Si no tiene esquema, determinar si es URL o búsqueda
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                DUCK_SEARCH + Uri.encode(url)
            }
        }

        if (addToHistory && currentUrl.isNotBlank() && currentUrl != url) {
            backStack.push(currentUrl)
            forwardStack.clear()
        }

        currentUrl = url
        txtUrl.text = cleanDisplayUrl(url)
        updateNavButtons()

        progressBar.visibility = View.VISIBLE
        errorLayout.visibility = View.GONE
        contentView.visibility = View.VISIBLE
        contentView.text = "Cargando página..."
        webScrollView.scrollTo(0, 0)

        bgExecutor.execute {
            var conn: HttpURLConnection? = null
            try {
                var currentDest = URL(url)
                var redirects = 0
                var finalHtml = ""
                var pageTitle = ""

                while (redirects < 5) {
                    conn = currentDest.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 12_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("User-Agent", USER_AGENT)
                    conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")

                    val code = conn.responseCode
                    if (code in 301..308) {
                        val newLocation = conn.getHeaderField("Location")
                        if (!newLocation.isNullOrBlank()) {
                            currentDest = URL(currentDest, newLocation)
                            currentUrl = currentDest.toString()
                            redirects++
                            conn.disconnect()
                            continue
                        }
                    }

                    if (code in 200..299) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                        val sb = java.lang.StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            sb.append(line).append("\n")
                            if (sb.length > 300_000) break // Limitar tamaño de descarga a 300 KB para no saturar RAM
                        }
                        reader.close()
                        finalHtml = sb.toString()
                        break
                    } else {
                        throw Exception("HTTP $code: ${conn.responseMessage}")
                    }
                }

                // Extraer título de la página si existe
                val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(finalHtml)
                if (titleMatcher.find()) {
                    pageTitle = titleMatcher.group(1)?.trim() ?: ""
                }

                // Limpiar scripts y estilos molestos
                var cleanHtml = finalHtml
                    .replace(Regex("(?s)<script.*?</script>"), "")
                    .replace(Regex("(?s)<style.*?</style>"), "")
                    .replace(Regex("(?s)<svg.*?</svg>"), "")
                    .replace(Regex("(?s)<noscript.*?</noscript>"), "")

                // Parsear a CharSequence formateado
                val parsedSpanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(cleanHtml, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(cleanHtml)
                }

                // Convertir enlaces URLSpan en enlaces interactivos de nuestro navegador
                val ssb = SpannableStringBuilder(parsedSpanned)
                val urlSpans = ssb.getSpans(0, ssb.length, URLSpan::class.java)
                for (span in urlSpans) {
                    val spanStart = ssb.getSpanStart(span)
                    val spanEnd = ssb.getSpanEnd(span)
                    val spanFlags = ssb.getSpanFlags(span)
                    val targetLink = span.url

                    ssb.removeSpan(span)
                    ssb.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            vibrateTick()
                            val resolved = try {
                                URL(URL(currentUrl), targetLink).toString()
                            } catch (_: Exception) {
                                targetLink
                            }
                            loadUrl(resolved, addToHistory = true)
                        }
                    }, spanStart, spanEnd, spanFlags)
                }

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (pageTitle.isNotBlank()) {
                        txtUrl.text = pageTitle
                    }
                    contentView.text = if (ssb.isBlank()) "Página vacía o sin contenido de texto" else ssb
                    webScrollView.scrollTo(0, 0)
                }

            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    contentView.visibility = View.GONE
                    errorLayout.visibility = View.VISIBLE
                    txtError.text = "Error al conectar:\n${e.message ?: e.javaClass.simpleName}"
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun goBack() {
        if (!backStack.isEmpty()) {
            forwardStack.push(currentUrl)
            val prev = backStack.pop()
            loadUrl(prev, addToHistory = false)
        } else {
            finish()
        }
    }

    private fun goForward() {
        if (!forwardStack.isEmpty()) {
            backStack.push(currentUrl)
            val next = forwardStack.pop()
            loadUrl(next, addToHistory = false)
        }
    }

    private fun updateNavButtons() {
        btnBack.alpha = if (backStack.isNotEmpty()) 1.0f else 0.4f
        btnForward.alpha = if (forwardStack.isNotEmpty()) 1.0f else 0.4f
    }

    private fun showUrlInputDialog() {
        val d = resources.displayMetrics.density
        val input = EditText(this).apply {
            hint = "Escribe término o URL..."
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8E8E93.toInt())
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * d
                setColor(0xFF2C2C2E.toInt())
            }
        }

        val container = FrameLayout(this).apply {
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Navegar o Buscar")
            .setView(container)
            .setPositiveButton("Ir") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotBlank()) loadUrl(q, addToHistory = true)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.dismiss()
                val q = input.text.toString().trim()
                if (q.isNotBlank()) loadUrl(q, addToHistory = true)
                true
            } else false
        }

        dialog.show()
        input.requestFocus()
    }

    private fun showBookmarksDialog() {
        val bookmarks = arrayOf(
            "🔍 Google Search",
            "🦆 DuckDuckGo Lite",
            "📖 Wikipedia en Español",
            "🌦️ El Tiempo (Madrid)",
            "📶 NeverSSL (Portal Cautivo)",
            "📄 Copiar enlace actual",
            "❌ Cerrar navegador"
        )

        AlertDialog.Builder(this)
            .setTitle("Marcadores y Opciones")
            .setItems(bookmarks) { _, which ->
                vibrateTick()
                when (which) {
                    0 -> loadUrl("https://www.google.com")
                    1 -> loadUrl("https://html.duckduckgo.com/html/")
                    2 -> loadUrl("https://es.m.wikipedia.org")
                    3 -> loadUrl("https://wttr.in/Madrid?format=3")
                    4 -> loadUrl("http://neverssl.com")
                    5 -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", currentUrl))
                        Toast.makeText(this, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    }
                    6 -> finish()
                }
            }
            .show()
    }

    private fun cleanDisplayUrl(u: String?): String {
        if (u.isNullOrBlank()) return "Buscar o URL..."
        return u.replace("https://", "").replace("http://", "").removePrefix("www.")
    }

    private fun vibrateTick() {
        runCatching { vibrator?.vibrate(25) }
    }

    override fun onBackPressed() {
        if (backStack.isNotEmpty()) {
            goBack()
        } else {
            super.onBackPressed()
        }
    }
}
