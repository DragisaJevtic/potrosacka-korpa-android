package dev.intelisystems.potrosackakorpa

import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/** Our screen around the embedded scanner; CaptureActivity owns camera permissions and lifecycle. */
class PortraitScannerActivity : CaptureActivity() {
    private var torchOn = false

    override fun initializeContent(): DecoratedBarcodeView {
        fun dp(value: Int) = KorpaUi.dp(this, value)
        fun spacing(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KorpaUi.paper)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(13), dp(20), dp(12))
        }
        header.addView(KorpaUi.button(this, "← Nazad") { finish() })
        header.addView(KorpaUi.label(this, "Skeniraj proizvod", 28, KorpaUi.ink, true).apply {
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }, spacing(12))
        header.addView(KorpaUi.label(this, "Postavi bar-kod u okvir i zadrži telefon mirno.", 15, KorpaUi.muted), spacing(8))
        root.addView(header)

        val scanner = DecoratedBarcodeView(this).apply {
            // Instructions belong to our UI, outside the live camera preview.
            statusView?.visibility = View.GONE
            contentDescription = "Kamera za skeniranje bar-koda proizvoda"
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        root.addView(scanner, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            marginStart = dp(20); marginEnd = dp(20)
        })

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(20))
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            val torch = KorpaUi.button(this, "Uključi svetlo") {
                if (torchOn) scanner.setTorchOff() else scanner.setTorchOn()
            }
            scanner.setTorchListener(object : DecoratedBarcodeView.TorchListener {
                override fun onTorchOn() { torchOn = true; updateTorchButton(torch) }
                override fun onTorchOff() { torchOn = false; updateTorchButton(torch) }
            })
            footer.addView(torch)
        }
        footer.addView(KorpaUi.label(this,
            "Kod se očitava automatski. Nakon očitavanja prikazaćemo cene proizvoda.",
            14, KorpaUi.muted), spacing(12))
        root.addView(footer)
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            root.requestApplyInsets()
        } else {
            window.statusBarColor = KorpaUi.paper
            window.navigationBarColor = if (Build.VERSION.SDK_INT >= 27) KorpaUi.paper else KorpaUi.forest
        }
        return scanner
    }

    private fun updateTorchButton(button: Button) {
        button.text = if (torchOn) "Isključi svetlo" else "Uključi svetlo"
        button.contentDescription = button.text
    }
}
