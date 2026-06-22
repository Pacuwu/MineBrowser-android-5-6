package com.minerva.minibrowser

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // El motor de Gecko: se crea UNA sola vez para toda la app
    private lateinit var geckoRuntime: GeckoRuntime

    // La "pantalla" donde se pinta la pestaña que esté activa
    private lateinit var geckoView: GeckoView

    // Lista de pestañas abiertas. Cada GeckoSession es una pestaña independiente
    private val sessions = mutableListOf<GeckoSession>()

    // Nombres que aparecen en el Spinner: "Pestaña 1", "Pestaña 2"...
    private val tabNames = mutableListOf<String>()
    private lateinit var tabAdapter: ArrayAdapter<String>

    // Historial de URLs visitadas (compartido entre todas las pestañas, simple)
    private val historial = mutableListOf<String>()

    private lateinit var addressBar: EditText
    private lateinit var spinnerTabs: Spinner
    private lateinit var progressBar: ProgressBar

    // Página de inicio, la usan tanto las pestañas nuevas como el botón [🏠]
    private val paginaDeInicio = "https://www.google.com"

    // Índice de la pestaña que se está mostrando ahora mismo
    private var currentTabIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.geckoView)
        addressBar = findViewById(R.id.etAddressBar)
        spinnerTabs = findViewById(R.id.spinnerTabs)
        progressBar = findViewById(R.id.progressBar)
        val btnGo: Button = findViewById(R.id.btnGo)
        val btnNewTab: Button = findViewById(R.id.btnNewTab)
        val btnCloseTab: Button = findViewById(R.id.btnCloseTab)
        val btnBack: Button = findViewById(R.id.btnBack)
        val btnForward: Button = findViewById(R.id.btnForward)
        val btnRefresh: Button = findViewById(R.id.btnRefresh)
        val btnHome: Button = findViewById(R.id.btnHome)
        val btnHistory: Button = findViewById(R.id.btnHistory)

        // 1) Arrancamos el motor Gecko. Le pasamos explícitamente el tamaño y la
        // densidad reales de la pantalla del móvil: en hardware antiguo/poco común
        // como el M2, GeckoView a veces no las calcula bien por su cuenta, y eso
        // hace que las webs se vean pequeñas/descuadradas (como en modo escritorio
        // en vez de modo móvil), pase lo que pase con userAgentMode/viewportMode.
        val metricas = resources.displayMetrics
        val ajustesRuntime = GeckoRuntimeSettings.Builder()
            .displayDensityOverride(metricas.density)
            .displayDpiOverride(metricas.densityDpi)
            .screenSizeOverride(metricas.widthPixels, metricas.heightPixels)
            .build()
        geckoRuntime = GeckoRuntime.create(this, ajustesRuntime)

        // 2) Spinner que lista las pestañas abiertas
        tabAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tabNames)
        tabAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTabs.adapter = tabAdapter

        spinnerTabs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != currentTabIndex) {
                    switchToTab(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3) Botón [+] -> crea una pestaña nueva
        btnNewTab.setOnClickListener { addNewTab() }

        // 4) Botón [✕] -> cierra la pestaña actual
        btnCloseTab.setOnClickListener { closeCurrentTab() }

        // Flechas de atrás / adelante -> navegan por el historial de la pestaña actual
        btnBack.setOnClickListener {
            if (currentTabIndex != -1) sessions[currentTabIndex].goBack()
        }
        btnForward.setOnClickListener {
            if (currentTabIndex != -1) sessions[currentTabIndex].goForward()
        }

        // Refrescar la página actual
        btnRefresh.setOnClickListener {
            if (currentTabIndex != -1) sessions[currentTabIndex].reload()
        }

        // Ir a la página de inicio en la pestaña actual
        btnHome.setOnClickListener {
            if (currentTabIndex != -1) sessions[currentTabIndex].loadUri(paginaDeInicio)
        }

        // Mostrar el historial de navegación
        btnHistory.setOnClickListener { mostrarHistorial() }

        // 5) Botón ➔ -> navega o busca lo que haya en la barra
        btnGo.setOnClickListener { goToAddressOrSearch() }

        // También aceptamos el botón "Ir" del teclado
        addressBar.setOnEditorActionListener { _, actionId, event ->
            val esEnter = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || esEnter) {
                goToAddressOrSearch()
                true
            } else {
                false
            }
        }

        // Al abrir la app, creamos la primera pestaña automáticamente
        addNewTab()
    }

    /**
     * Crea una GeckoSession nueva configurada en modo MÓVIL (para que las webs
     * carguen su versión para móvil en vez de la de escritorio), le conecta
     * los delegados (pop-ups, progreso de carga, historial) y la añade a la
     * lista de pestañas. NO la selecciona ni le carga ninguna URL todavía.
     */
    private fun crearSesion(): GeckoSession {
        val ajustes = GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()

        val session = GeckoSession(ajustes)
        session.open(geckoRuntime)
        configurarNavegacion(session)
        configurarProgreso(session)

        sessions.add(session)
        tabNames.add("Pestaña ${sessions.size}")
        tabAdapter.notifyDataSetChanged()

        return session
    }

    /**
     * Junta dos cosas en un solo delegado (GeckoView solo permite UNO por sesión):
     * 1) Pop-ups: por defecto GeckoView los bloquea sin avisar; aquí los permitimos
     *    creando una pestaña nueva de verdad para ellos.
     * 2) Historial + barra de direcciones: cada vez que la página cambia de URL,
     *    la guardamos en el historial y, si es la pestaña visible, actualizamos
     *    el texto de la barra de arriba.
     */
    private fun configurarNavegacion(session: GeckoSession) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                // IMPORTANTE: GeckoView exige recibir una sesión que NO esté abierta todavía.
                // Si llamamos a session.open() antes de devolverla, GeckoView lanza
                // "Must use an unopened GeckoSession instance" y la app se cierra.
                // La solución: crear la sesión SIN abrirla, devolverla, y dejar que
                // GeckoView la abra él solo. Nosotros la registramos y la mostramos
                // después, cuando recibimos el primer LocationChange de ella.
                val ajustes = GeckoSessionSettings.Builder()
                    .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                    .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                    .build()

                val nuevaSesion = GeckoSession(ajustes)
                // Configuramos el progreso ANTES de abrirla para no perder eventos
                configurarProgreso(nuevaSesion)

                // Registramos la sesión en nuestra lista
                sessions.add(nuevaSesion)
                tabNames.add("Pestaña ${sessions.size}")
                tabAdapter.notifyDataSetChanged()

                val nuevoIndice = sessions.size - 1

                // Cuando GeckoView la abra y cargue algo, nos llegará onLocationChange
                // y entonces la mostramos. Pero ya la ponemos activa en el spinner.
                runOnUiThread {
                    spinnerTabs.setSelection(nuevoIndice)
                    // Esperamos al primer LocationChange antes de hacer setSession,
                    // pero ya actualizamos currentTabIndex para que progreso funcione
                    currentTabIndex = nuevoIndice
                }

                // También necesita su propio navigationDelegate para futuros popups
                configurarNavegacion(nuevaSesion)

                // Devolvemos la sesión SIN abrir — GeckoView la abre él mismo
                return GeckoResult.fromValue(nuevaSesion)
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (!url.isNullOrBlank() && (historial.isEmpty() || historial.last() != url)) {
                    historial.add(url)
                }

                val indice = sessions.indexOf(session)
                if (indice == currentTabIndex) {
                    // Si el GeckoView no está mostrando esta sesión todavía
                    // (ocurre con popups recién abiertos por GeckoView), la conectamos.
                    // Para sesiones normales geckoView.session ya ES esta sesión,
                    // así que la condición es false y NO llamamos setSession de nuevo
                    // (eso era lo que provocaba el parpadeo en blanco en algunas webs).
                    if (geckoView.session !== session) {
                        runOnUiThread { geckoView.setSession(session) }
                    }
                    if (url != null) {
                        runOnUiThread { addressBar.setText(url) }
                    }
                }
            }
        }
    }

    /** Conecta la barra de progreso con la carga de esta sesión (solo se ve/actualiza
     *  si la sesión que está cargando es la que se está mostrando ahora mismo). */
    private fun configurarProgreso(session: GeckoSession) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (sessions.indexOf(session) == currentTabIndex) {
                    progressBar.progress = 0
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (sessions.indexOf(session) == currentTabIndex) {
                    progressBar.progress = progress
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (sessions.indexOf(session) == currentTabIndex) {
                    progressBar.visibility = View.GONE
                }
            }
        }

        // Cuando el proceso de contenido de GeckoView se cae (por memoria escasa,
        // una página muy pesada, etc.) la pantalla se queda en blanco y no responde.
        // Con este delegate lo detectamos y reabrimos la sesión + recargamos la página
        // automáticamente, igual que haría Chrome/Firefox con el mensaje "Volver a cargar".
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session: GeckoSession) {
                val ultimaUrl = if (historial.isNotEmpty()) historial.last() else paginaDeInicio
                // La sesión queda inutilizable tras un crash: hay que reabrirla
                session.open(geckoRuntime)
                session.loadUri(ultimaUrl)
            }
        }
    }

    /** Muestra el historial en una lista; al tocar una entrada, la abre en la pestaña actual */
    private fun mostrarHistorial() {
        if (currentTabIndex == -1 || historial.isEmpty()) return

        // Lo más reciente arriba
        val items = historial.asReversed().toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Historial")
            .setItems(items) { _, which ->
                sessions[currentTabIndex].loadUri(items[which])
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** Crea una pestaña nueva, la selecciona y carga la página de inicio en ella */
    private fun addNewTab() {
        val session = crearSesion()

        val nuevoIndice = sessions.size - 1
        spinnerTabs.setSelection(nuevoIndice)
        switchToTab(nuevoIndice)

        session.loadUri(paginaDeInicio)
    }

    /** Cierra la pestaña que se está viendo ahora mismo. Si era la única, abre una en blanco. */
    private fun closeCurrentTab() {
        if (currentTabIndex == -1 || sessions.isEmpty()) return

        val indiceACerrar = currentTabIndex
        sessions[indiceACerrar].close()
        sessions.removeAt(indiceACerrar)
        tabNames.removeAt(indiceACerrar)

        // Si era la única pestaña, no dejamos la app sin ninguna: abrimos una nueva
        if (sessions.isEmpty()) {
            tabAdapter.notifyDataSetChanged()
            addNewTab()
            return
        }

        // Renombramos las que quedan para que sigan siendo "Pestaña 1", "Pestaña 2"...
        for (i in tabNames.indices) {
            tabNames[i] = "Pestaña ${i + 1}"
        }
        tabAdapter.notifyDataSetChanged()

        // Mostramos la pestaña anterior (o la primera, si cerramos la primera)
        val nuevoIndice = if (indiceACerrar > 0) indiceACerrar - 1 else 0
        spinnerTabs.setSelection(nuevoIndice)
        switchToTab(nuevoIndice)
    }

    /** Muestra en la GeckoView la sesión correspondiente a esa pestaña */
    private fun switchToTab(index: Int) {
        if (index < 0 || index >= sessions.size) return
        currentTabIndex = index
        geckoView.setSession(sessions[index])
        progressBar.visibility = View.GONE
    }

    /** Lee el texto de la barra de direcciones y decide si navegar o buscar */
    private fun goToAddressOrSearch() {
        if (currentTabIndex == -1) return

        val texto = addressBar.text.toString().trim()
        if (texto.isEmpty()) return

        sessions[currentTabIndex].loadUri(construirUrl(texto))
        addressBar.clearFocus()
    }

    /**
     * Si el texto tiene un "." y no tiene espacios, lo tratamos como URL.
     * Si no, lo convertimos en una búsqueda de Google.
     */
    private fun construirUrl(texto: String): String {
        val pareceUrl = texto.contains(".") && !texto.contains(" ")

        return if (pareceUrl) {
            if (texto.startsWith("http://") || texto.startsWith("https://")) {
                texto
            } else {
                "https://$texto"
            }
        } else {
            val consultaCodificada = URLEncoder.encode(texto, "UTF-8")
            "https://www.google.com/search?q=$consultaCodificada"
        }
    }
}
