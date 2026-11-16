package dev.intelisystems.potrosackakorpa

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.ComponentActivity
import com.journeyapps.barcodescanner.ScanContract
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.CancellationSignal
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private companion object {
        val io = Executors.newSingleThreadExecutor()
        val syncWorker = Executors.newSingleThreadExecutor()
        val paper = KorpaUi.paper
        val ink = KorpaUi.ink
        val muted = KorpaUi.muted
        val forest = KorpaUi.forest
        val mint = KorpaUi.mint
        val white = KorpaUi.white
        val warning = KorpaUi.warning
    }

    private val catalog get() = (application as KorpaApplication).catalog
    private var unregisterCategoryBack: (() -> Unit)? = null
    private lateinit var body: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var navigation: LinearLayout
    private var showingList = true
    private var category: Category? = null
    private var query = ""
    private var list = emptyList<ShoppingItem>()
    private var categories = emptyList<Category>()
    private var hasCatalog = false
    private var loadingMessage = "Otvaramo sačuvani katalog…"
    private var syncing = false
    private var syncError: String? = null
    private var results: LinearLayout? = null
    private var searchRequest = 0
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var searchCancellation: CancellationSignal? = null
    private var keyboardVisible = false
    private var pendingKeyboardHide: Runnable? = null

    private fun cancelKeyboardHide() {
        pendingKeyboardHide?.let { searchHandler.removeCallbacks(it) }
        pendingKeyboardHide = null
    }

    private fun scheduleKeyboardHide() {
        cancelKeyboardHide()
        val input = currentFocus as? EditText ?: return
        val task = Runnable {
            pendingKeyboardHide = null
            if (!isDestroyed && !isFinishing && hasWindowFocus() &&
                input.isAttachedToWindow && currentFocus === input) {
                hideKeyboard()
            }
        }
        pendingKeyboardHide = task
        searchHandler.postDelayed(task, 3_000L)
    }

    override fun onStop() {
        cancelKeyboardHide()
        super.onStop()
    }

    private fun cancelSearch() {
        searchRequest++
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        pendingSearch = null
        searchCancellation?.cancel()
        searchCancellation = null
    }
    private var editingList = false
    private val scannerLauncher = registerForActivityResult(ScanContract()) { result ->
        barcodeLookup.onScanResult(result)
    }
    private val barcodeLookup: BarcodeLookup by lazy { BarcodeLookup(this, { offer -> addOffer(offer) }) { options -> scannerLauncher.launch(options) } }
    private val money = NumberFormat.getNumberInstance(Locale.forLanguageTag("sr-Latn-RS")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showingList = savedInstanceState?.getBoolean("list", true) ?: true
        category = savedInstanceState?.getString("category")?.let {
            Category(it, savedInstanceState.getString("categoryLabel", it))
        }
        query = savedInstanceState?.getString("query", "").orEmpty()
        val root = column().apply { setBackgroundColor(paper) }
        header = column().apply { setPadding(dp(20), dp(13), dp(20), dp(10)) }
        body = FrameLayout(this)
        navigation = row().apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(white)
        }
        root.addView(header)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(navigation)
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                val visible = insets.isVisible(WindowInsets.Type.ime())
                if (keyboardVisible != visible) {
                    keyboardVisible = visible
                    updateCategoryBack()
                }
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                insets
            }
        } else {
            window.statusBarColor = paper
            window.navigationBarColor = if (Build.VERSION.SDK_INT >= 27) white else forest
        }
        render()
        Log.i("KorpaSync", "Activity created; loading local catalog")
        refreshFromDisk()
    }

    override fun onResume() {
        super.onResume()
        synchronize()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("list", showingList)
        outState.putString("category", category?.key)
        outState.putString("categoryLabel", category?.name)
        outState.putString("query", query)
    }

    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (keyboardVisible) { hideKeyboard(); return }
        // Android 12 and earlier dispatch Back here.
        if (!showingList && (category != null || query.isNotBlank())) showCategories() else super.onBackPressed()
    }

    private fun updateCategoryBack() {
        unregisterCategoryBack?.invoke()
        unregisterCategoryBack = null
        if (Build.VERSION.SDK_INT >= 33 && !keyboardVisible && !showingList && (category != null || query.isNotBlank())) {
            val callback = OnBackInvokedCallback { showCategories() }
            val dispatcher = onBackInvokedDispatcher
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            unregisterCategoryBack = { dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
    }

    override fun onDestroy() {
        cancelKeyboardHide()
        cancelSearch()
        barcodeLookup.close()
        unregisterCategoryBack?.invoke()
        super.onDestroy()
    }

    private fun onScreen(action: () -> Unit) = runOnUiThread { if (!isDestroyed && !isFinishing) action() }

    private fun refreshFromDisk(keepSearch: Boolean = false) {
        io.execute {
            val ready = catalog.version() != null
            val availableCategories = catalog.categories()
            val savedItems = catalog.shoppingList()
            onScreen {
                Log.i("KorpaSync", "Local catalog ready=$ready categories=${availableCategories.size} shoppingItems=${savedItems.size}")
                hasCatalog = ready
                categories = availableCategories
                list = savedItems
                if (keepSearch && results != null && !showingList) {
                    loadOffers()
                    renderHeader()
                } else {
                    render()
                }
            }
        }
    }

    private fun synchronize() {
        if (syncing) return
        syncing = true
        syncError = null
        renderHeader()
        syncWorker.execute {
            val outcome = runCatching {
                catalog.sync { message ->
                    onScreen {
                        loadingMessage = message
                        if (!hasCatalog && list.isEmpty()) {
                            body.removeAllViews()
                            renderInitialState()
                        }
                    }
                }
            }
            outcome.exceptionOrNull()?.let { Log.e("KorpaSync", "Catalog synchronization failed", it) }
            onScreen {
                syncing = false
                syncError = outcome.exceptionOrNull()?.let(::syncMessage)
                if (outcome.getOrNull() == true) refreshFromDisk(keepSearch = true)
                else if (!hasCatalog) render()
                else renderHeader()
            }
        }
    }

    private fun syncMessage(error: Throwable): String = when {
        BuildConfig.API_URL.isEmpty() -> "Adresa servera nije podešena."
        error is CatalogHttpError -> when (error.status) {
            404 -> "Na podešenoj adresi nema API-ja za mobilni katalog (404)."
            503 -> "Server još priprema katalog ili mu baza trenutno nije dostupna (503)."
            else -> "Server je vratio grešku pri učitavanju kataloga (${error.status})."
        }
        error is SocketTimeoutException -> "Server nije odgovorio na vreme."
        error is IOException -> "Veza sa serverom kataloga nije uspela. Proveri mrežu i adresu servera."
        else -> "Katalog trenutno ne može da se osveži."
    }

    private fun render() {
        cancelKeyboardHide()
        results = null
        updateCategoryBack()
        cancelSearch()
        body.removeAllViews()
        renderHeader()
        navigation.removeAllViews()
        tab("▤", "Spisak", showingList) {
            if (!showingList) {
                hideKeyboard()
                showingList = true
                render()
            }
        }
        tab("▦", "Kategorije", !showingList) {
            if (showingList) {
                showingList = false
                render()
            } else if (category != null || query.isNotBlank()) showCategories()
        }
        tab("▥", "Skeniraj", false) { barcodeLookup.show() }
        when {
            !hasCatalog && list.isEmpty() -> renderInitialState()
            showingList -> renderList()
            category != null -> renderSearch(requireNotNull(category))
            else -> renderCategories()
        }
    }

    private fun renderHeader() {
        header.removeAllViews()
        val heading = row().apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(this).apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_korpa, 0, 0, 0)
            compoundDrawablesRelative[0]?.setBounds(0, 0, dp(24), dp(24))
            setCompoundDrawablesRelative(compoundDrawablesRelative[0], null, null, null)
            compoundDrawablePadding = dp(8)
            setText(R.string.app_name)
            setTextColor(forest)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (hasCatalog) heading.addView(button(if (syncing) "Osvežavanje…" else "Osveži", mint, forest) { synchronize() }
            .apply { isEnabled = !syncing })
        header.addView(heading)
        if (hasCatalog && syncError != null) {
            header.addView(label("${syncError.orEmpty()} Prikazane su poslednje preuzete cene.", 13, warning).apply { setPadding(0, dp(6), 0, 0) })
        }
    }

    private fun tab(icon: String, name: String, selected: Boolean, action: () -> Unit) {
        val tab = button("$icon  $name", if (selected) mint else white, if (selected) forest else muted, action)
        tab.isSelected = selected
        navigation.addView(tab, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
    }

    private fun renderInitialState() {
        val center = column().apply { gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        body.addView(center, FrameLayout.LayoutParams(-1, -1))
        if (syncing || syncError == null) {
            center.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(42), dp(42)))
            center.addView(label("Preuzimamo katalog", 21, ink, true), margins(16))
            center.addView(label(loadingMessage, 15, muted), margins(8))
        } else {
            center.addView(label(syncError.orEmpty(), 18, ink, true).apply { gravity = Gravity.CENTER })
            center.addView(button("Pokušaj ponovo", forest, white) { synchronize(); render() }, margins(16))
        }
    }

    private fun renderList() {
        val page = scrollingPage()
        page.addView(title("Spisak za kupovinu"))
        val missing = list.count { !it.available }
        val total = list.filter { it.available }.sumOf { it.offer.price * it.quantity }
        val summary = column().apply {
            background = rounded(forest, 24)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        summary.addView(label(if (missing > 0) "Procena za dostupne ponude" else "Procenjeni iznos", 14, mint, true))
        summary.addView(label(if (list.isNotEmpty() && missing == list.size) "—" else rsd(total), 32, white, true), margins(3))
        summary.addView(label("Prema objavljenim cenama izabranih ponuda.", 13, mint), margins(6))
        page.addView(summary, margins(15))
        if (list.isNotEmpty()) {
            page.addView(button("Najjeftinija kupovina", mint, forest) {
                startActivity(android.content.Intent(this, CheapestActivity::class.java))
            }, margins(12))
        }
        if (missing > 0) {
            page.addView(label("Neke ponude sa spiska više nisu u katalogu.", 14, warning, true), margins(14))
        }
        if (list.isEmpty()) {
            val empty = card()
            empty.addView(label("Spisak je prazan", 20, ink, true))
            empty.addView(label("Pronađi artikle i dodaj ih iz kategorija.", 15, muted), margins(5))
            empty.addView(button("Otvori kategorije", mint, forest) { showCategories() }, margins(14))
            page.addView(empty, margins(16))
        } else {
            list.forEach { item ->
                val entry = card()
                offerInfo(entry, item.offer)
                val line = row().apply { gravity = Gravity.CENTER_VERTICAL }
                line.addView(label(if (item.available) rsd(item.offer.price * item.quantity) else "Ponuda nedostupna",
                    15, if (item.available) forest else warning, true), LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(button("−", paper, forest) { changeQuantity(item, -1) }.apply {
                    contentDescription = if (item.quantity == 1) "Ukloni ${item.offer.name}" else "Smanji količinu"
                })
                line.addView(label(item.quantity.toString(), 15, ink, true).apply {
                    gravity = Gravity.CENTER
                    minWidth = dp(36)
                })
                line.addView(button("+", paper, forest) { changeQuantity(item, 1) }.apply {
                    contentDescription = "Povećaj količinu"
                    isEnabled = item.available && item.quantity < 999
                })
                entry.addView(line, margins(9))
                page.addView(entry, margins(10))
            }
        }
    }

    private fun changeQuantity(item: ShoppingItem, difference: Int) {
        if (editingList) return
        editingList = true
        val y = body.getChildAt(0)?.scrollY ?: 0
        io.execute {
            val saved = runCatching { catalog.setQuantity(item.offer.id, item.quantity + difference) }
            val items = catalog.shoppingList()
            onScreen {
                editingList = false
                list = items
                if (saved.isFailure) toast("Izmena nije sačuvana.")
                if (showingList) {
                    render()
                    body.getChildAt(0)?.let { view -> view.post { view.scrollTo(0, y) } }
                }
            }
        }
    }

    private fun showCategories() {
        hideKeyboard()
        showingList = false
        category = null
        query = ""
        render()
    }

    private fun renderCategories() {
        val layout = column().apply {
            isFocusableInTouchMode = true
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        body.addView(layout, FrameLayout.LayoutParams(-1, -1))
        layout.addView(title("Kategorije"))
        val input = KorpaUi.input(EditText(this)).apply {
            hint = "Pretraži sve proizvode ili prodavnice"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(query)
        }
        layout.addView(input, margins(12))
        val clear = button("Obriši pretragu", mint, forest) { input.setText("") }
        layout.addView(clear, margins(8))
        val page = column().apply { setPadding(0, 0, 0, dp(14)) }
        val scroll = ScrollView(this).apply { addView(page) }
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (categories.isEmpty()) {
            page.addView(label("Katalog trenutno nema ponuda.", 16, muted), margins(16))
            return
        }
        val grid = column()
        categories.chunked(2).forEach { pair ->
            val categoryRow = row()
            pair.forEachIndexed { index, item ->
                val tile = column().apply {
                    gravity = Gravity.CENTER
                    minimumHeight = dp(126)
                    setPadding(dp(7), dp(7), dp(7), dp(7))
                    background = rounded(white, 18)
                    addView(button(item.icon, mint, forest) {
                        val children = catalog.subcategories(item)
                        if (children.size > 1) {
                            val choices = listOf(item.copy(name = "Sve: ${item.name}")) + children
                            AlertDialog.Builder(this@MainActivity).setTitle(item.name)
                                .setItems(choices.map { it.name }.toTypedArray()) { _, position ->
                                    category = if (position == 0) item else choices[position]
                                    query = ""
                                    render()
                                }.setNegativeButton("Nazad", null).show()
                        } else {
                            category = item
                            query = ""
                            render()
                        }
                    }.apply { contentDescription = item.name; textSize = 26f }, LinearLayout.LayoutParams(dp(60), dp(60)))
                    addView(label(item.name, 13, ink, true).apply {
                        gravity = Gravity.CENTER
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
                }
                categoryRow.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    if (index == 0) marginEnd = dp(8)
                })
            }
            if (pair.size == 1) {
                categoryRow.addView(View(this), LinearLayout.LayoutParams(0, -1, 1f))
            }
            grid.addView(categoryRow, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
        page.addView(grid, margins(16))
        val cards = column()
        results = cards
        page.addView(cards)
        fun searchAll() {
            val searching = query.isNotBlank()
            grid.visibility = if (searching) View.GONE else View.VISIBLE
            cards.visibility = if (searching) View.VISIBLE else View.GONE
            clear.visibility = if (searching) View.VISIBLE else View.GONE
            scroll.scrollTo(0, 0)
            updateCategoryBack()
            if (searching) loadOffers() else { cancelSearch(); cards.removeAllViews() }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s.toString()
                scheduleKeyboardHide()
                searchAll()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        layout.requestFocus()
        searchAll()
    }

    private fun renderSearch(selected: Category) {
        val layout = column().apply {
            isFocusableInTouchMode = true
            setPadding(dp(20), dp(2), dp(20), 0)
        }
        body.addView(layout, FrameLayout.LayoutParams(-1, -1))
        layout.addView(button("‹  Kategorije", mint, forest) { showCategories() })
        layout.addView(title(selected.name), margins(10))
        val input = EditText(this).apply {
            hint = "Naziv artikla ili prodavnice"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(query)
            KorpaUi.input(this)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s.toString()
                    scheduleKeyboardHide()
                    loadOffers()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        layout.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val cards = column().apply { setPadding(0, 0, 0, dp(14)) }
        results = cards
        layout.addView(ScrollView(this).apply { addView(cards) }, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.requestFocus()
        loadOffers()
    }

    private fun loadOffers(offset: Int = 0) {
        val selected = category
        val cards = results ?: return
        cancelSearch()
        val token = searchRequest
        val search = query
        if (selected == null && search.isBlank()) return
        val cancellation = CancellationSignal()
        searchCancellation = cancellation
        if (offset == 0) cards.removeAllViews()
        val loadingText = label("Pripremamo pretragu…", 15, muted).apply { setPadding(dp(12), 0, 0, 0) }
        val loading = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ProgressBar(this@MainActivity), LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(loadingText)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        cards.addView(loading, margins(16))
        val task = Runnable {
            io.execute {
                if (cancellation.isCanceled) return@execute
                val outcome = runCatching {
                    catalog.searchOffers(selected?.key, search, offset, cancellation) { total ->
                        onScreen {
                            if (token == searchRequest && cards == results && !cancellation.isCanceled) {
                                loadingText.text = "Pretraga u toku… Ukupno artikala: ${formatCount(total)}"
                            }
                        }
                    }
                }
                onScreen {
                    if (token != searchRequest || cards != results || cancellation.isCanceled) return@onScreen
                    cards.removeView(loading)
                    val page = outcome.getOrElse {
                        Log.e("KorpaSearch", "Search failed", it)
                        cards.addView(label("Pretraga nije uspela. Pokušaj ponovo.", 15, warning), margins(12))
                        cards.addView(button("Pokušaj ponovo", mint, forest) { loadOffers(offset) }, margins(8))
                        return@onScreen
                    }
                    val found = page.offers
                    val summary = cards.findViewWithTag<TextView>("search-count") ?: label("", 15, forest, true).apply {
                        tag = "search-count"
                        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                        cards.addView(this, 0, margins(12))
                    }
                    val shown = minOf(offset.toLong() + minOf(found.size, 30), page.matched)
                    summary.text = "Pronađeno: ${articleCount(page.matched)} · Prikazano: ${formatCount(shown)}\n" +
                        "Pretraženo: ${articleCount(page.total)}. Isti proizvod kod različitih prodavaca računa se zasebno."
                    if (found.isEmpty() && offset == 0) {
                        cards.addView(label("Nema ponuda za ovu pretragu.", 15, muted), margins(16))
                    }
                    found.take(30).forEach { offer ->
                        val entry = card()
                        offerInfo(entry, offer, showRetailer = false)
                        val line = row().apply { gravity = Gravity.CENTER_VERTICAL }
                        val prices = column()
                        prices.addView(label(if (offer.minPrice == offer.maxPrice) rsd(offer.price)
                            else "${rsd(offer.minPrice)} – ${rsd(offer.maxPrice)}", 17, forest, true))
                        if (offer.minPrice != offer.maxPrice) {
                            prices.addView(label("U katalogu · ova ponuda: ${rsd(offer.price)}", 12, muted))
                        }
                        line.addView(prices, LinearLayout.LayoutParams(0, -2, 1f))
                        line.addView(button("+  Dodaj", mint, forest) { addOffer(offer) }.apply {
                            contentDescription = "Dodaj ${offer.name} na spisak"
                        })
                        entry.addView(line, margins(8))
                        offer.barcode?.takeIf(::validProductCode)?.let { code ->
                            entry.addView(button("Uporedi cene i istoriju", mint, forest) { barcodeLookup.showProduct(code) }, margins(8))
                        }
                        cards.addView(entry, margins(9))
                    }
                    if (found.size > 30) {
                        val more = button("Prikaži još", mint, forest) {
                            cards.removeViewAt(cards.childCount - 1)
                            loadOffers(offset + 30)
                        }
                        cards.addView(more, margins(10))
                    }
                }
            }
        }
        pendingSearch = task
        searchHandler.postDelayed(task, if (offset == 0) 300L else 0L)
    }

    private fun formatCount(count: Long) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("sr-Latn-RS")).format(count)
    private fun articleCount(count: Long): String {
        val ending = if (count % 10 == 1L && count % 100 != 11L) "artikal" else "artikala"
        return "${formatCount(count)} $ending"
    }

    private fun addOffer(offer: Offer) {
        io.execute {
            val result = runCatching { catalog.add(offer) }
            val items = catalog.shoppingList()
            onScreen {
                list = items
                if (showingList) render()
                toast(if (result.isSuccess) "Dodato na spisak" else "Artikal nije dodat.")
            }
        }
    }

    private fun offerInfo(parent: LinearLayout, offer: Offer, showRetailer: Boolean = true) {
        parent.addView(label(offer.name, 16, ink, true))
        val detail = listOfNotNull(offer.retailer.takeIf { showRetailer }, offer.unit).filter { it.isNotBlank() }.joinToString(" · ")
        if (detail.isNotEmpty()) parent.addView(label(detail, 13, muted), margins(4))
    }

    private fun hideKeyboard() {
        cancelKeyboardHide()
        val focused = currentFocus ?: return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    private fun scrollingPage(): LinearLayout {
        val page = column().apply { setPadding(dp(20), dp(4), dp(20), dp(20)) }
        body.addView(ScrollView(this).apply { addView(page) }, FrameLayout.LayoutParams(-1, -1))
        return page
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun card() = KorpaUi.card(this)

    private fun title(value: String) = label(value, 28, ink, true).apply {
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = KorpaUi.label(this, value, size, color, bold)

    private fun button(value: String, fill: Int, color: Int, action: () -> Unit) =
        KorpaUi.styleButton(Button(this), fill, color).apply { text = value; setOnClickListener { action() } }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun margins(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2).apply { topMargin = dp(top) }
    private fun dp(size: Int) = (size * resources.displayMetrics.density + .5f).toInt()
    private fun rsd(para: Long) = "${money.format(java.math.BigDecimal.valueOf(para, 2))} RSD"
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
