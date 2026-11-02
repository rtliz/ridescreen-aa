// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import java.util.ArrayDeque
import java.util.UUID

/**
 * Read-only Bluetooth LE survey of the bike (owner's request, 2 Nov 2026: "can the app show RPM over
 * the bike's Bluetooth?"). Scans, then for the chosen device: lists every service/characteristic,
 * READS the readable ones, and SUBSCRIBES to every notify/indicate one, logging each frame with a
 * timestamp while the rider idles and revs the engine. The rider shares the log for analysis.
 *
 * Safety: there is NO characteristic write anywhere in this class. The only GATT writes are the
 * standard CCCD descriptors that switch notifications on. The one exception is opt-in: on the
 * keyless box (Nordic UART + the saved PKE code) it can repeat [PkeBattery]'s existing read-only
 * battery query (`008`), built by [PkeBattery.frame] — never lock/unlock/start commands.
 */
@SuppressLint("MissingPermission")
class BleSurveyActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    /** Scan stop + ask loop only, so clearing them never drops queued log lines. */
    private val timers = Handler(Looper.getMainLooper())
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var devices: LinearLayout
    private lateinit var scanBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var askBtn: Button

    private val lines = ArrayList<String>()
    private var t0 = SystemClock.elapsedRealtime()
    private var gatt: BluetoothGatt? = null
    private val ops = ArrayDeque<() -> Boolean>()
    private var opBusy = false
    private var uartRx: BluetoothGattCharacteristic? = null
    private var asking = false
    private var frameCount = 0
    private var stopScan: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = ScreenUi.dp(this, 16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.bg))
            setPadding(pad, 0, pad, 0)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(pad, b.top, pad, b.bottom)
            insets
        }
        root.addView(ScreenUi.header(this, getString(R.string.ble_survey_title)))
        root.addView(text(getString(R.string.ble_survey_steps), 13f, R.color.text_secondary))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scanBtn = button(R.string.ble_survey_scan) { scan() }
        stopBtn = button(R.string.ble_survey_stop) { disconnect("stopped by rider") }.apply { isEnabled = false }
        val shareBtn = button(R.string.ble_survey_share) { share() }
        for (b in listOf(scanBtn, stopBtn, shareBtn)) bar.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)
        askBtn = button(R.string.ble_survey_ask_off) { toggleAsk() }.apply { isEnabled = false }
        root.addView(askBtn)

        devices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(devices)
        logView = text("", 11f, R.color.text_primary).apply { typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(logView) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        log("RideScreen BLE survey · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · read only")
    }

    override fun onDestroy() {
        disconnect("screen closed")
        super.onDestroy()
    }

    // ------------------------------------------------------------------ scan

    private fun permsOk(): Boolean {
        val need = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return true
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), 4711)
        return false
    }

    private fun scan() {
        if (!permsOk()) return
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { toast(R.string.ble_survey_bt_off); return }
        val scanner = adapter.bluetoothLeScanner ?: return
        disconnect(null)
        devices.removeAllViews()
        val seen = LinkedHashMap<String, ScanResult>()
        val pke = PkeBattery.code(this)
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) { synchronized(seen) { seen[r.device.address] = r } }
        }
        scanBtn.isEnabled = false
        log("scanning 10 s…")
        runCatching {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        }.onFailure { log("scan failed: $it"); scanBtn.isEnabled = true; return }
        stopScan = { runCatching { scanner.stopScan(cb) }; stopScan = null }
        timers.postDelayed({
            stopScan?.invoke()
            scanBtn.isEnabled = true
            val list = synchronized(seen) { seen.values.sortedByDescending { it.rssi } }
            log("found ${list.size} devices")
            for (r in list.take(25)) {
                val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull() ?: "?"
                val uart = r.scanRecord?.serviceUuids?.any { it.uuid == UART_SERVICE } == true
                val isBox = pke.length >= 8 && PkeBattery.matches(r.scanRecord?.bytes, name, pke)
                val uuids = r.scanRecord?.serviceUuids?.joinToString { it.uuid.toString().take(8) }.orEmpty()
                val tag = when { isBox -> " ★ keyless box"; uart -> " · UART"; else -> "" }
                log("  ${r.rssi} dBm  '$name'  ${mac(r.device.address)}$tag  [$uuids]")
                devices.addView(button(0, "${r.rssi} dBm  $name$tag") { connect(r.device, name, isBox || uart) })
            }
        }, 10_000)
    }

    // ------------------------------------------------------------------ connect + survey

    private fun connect(dev: BluetoothDevice, name: String, uart: Boolean) {
        disconnect(null)
        devices.removeAllViews()
        t0 = SystemClock.elapsedRealtime()
        frameCount = 0
        log("connecting to '$name' ${mac(dev.address)} …")
        stopBtn.isEnabled = true
        askBtn.isEnabled = false
        gatt = runCatching { dev.connectGatt(this, false, callback, BluetoothDevice.TRANSPORT_LE) }
            .onFailure { log("connect failed: $it") }.getOrNull()
        if (uart) log("(keyless box / UART device: the 'ask battery' button repeats the read-only 008 query)")
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("connected (status=$status) → MTU + services")
                if (!g.requestMtu(247)) g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("disconnected (status=$status) after $frameCount frames")
                main.post { stopBtn.isEnabled = false; askBtn.isEnabled = false; asking = false }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { log("MTU $mtu"); g.discoverServices() }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            log("services (status=$status):")
            for (s in g.services) {
                log("  service ${s.uuid}")
                for (c in s.characteristics) {
                    log("    char ${c.uuid}  ${props(c.properties)}")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        queue { g.readCharacteristic(c) }
                    }
                    val notify = c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)
                    val d = c.getDescriptor(CCC)
                    if (notify != 0 && d != null) {
                        val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        queue {
                            g.setCharacteristicNotification(c, true)
                            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value) == 0
                            else @Suppress("DEPRECATION") run { d.value = value; g.writeDescriptor(d) }
                        }
                    }
                }
            }
            uartRx = g.getService(UART_SERVICE)?.getCharacteristic(UART_RX)
            if (uartRx != null && PkeBattery.code(this@BleSurveyActivity).length >= 8) main.post { askBtn.isEnabled = true }
            log("listening — now start the engine, idle, then rev (see steps above)")
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            log("read ${short(c.uuid)} (status=$status): ${fmt(value)}")
            next()
        }

        @Deprecated("pre-33")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION") log("read ${short(c.uuid)} (status=$status): ${fmt(c.value ?: ByteArray(0))}")
            next()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            log("notify on ${short(d.characteristic.uuid)} (status=$status)")
            next()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { next() }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = frame(c, value)

        @Deprecated("pre-33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION") frame(c, c.value ?: return)
        }
    }

    private fun frame(c: BluetoothGattCharacteristic, value: ByteArray) {
        frameCount++
        log("◀ ${short(c.uuid)}: ${fmt(value)}")
    }

    // ------------------------------------------------------------------ GATT op queue (one at a time)

    private fun queue(op: () -> Boolean) {
        synchronized(ops) { ops.add(op) }
        if (!opBusy) next()
    }

    private fun next() {
        while (true) {
            val op = synchronized(ops) { ops.poll() } ?: run { opBusy = false; return }
            opBusy = true
            if (runCatching { op() }.getOrDefault(false)) return
        }
    }

    /** Opt-in: every 3 s, PkeBattery's read-only battery query (008) to the keyless box. */
    private fun toggleAsk() {
        asking = !asking
        askBtn.setText(if (asking) R.string.ble_survey_ask_on else R.string.ble_survey_ask_off)
        if (asking) askLoop()
    }

    private fun askLoop() {
        if (!asking) return
        val g = gatt ?: return
        val rx = uartRx ?: return
        val pke = PkeBattery.code(this)
        if (pke.length < 8) return
        val bytes = PkeBattery.frame(pke, PkeBattery.Query.HANDSHAKE)
        queue {
            val type = if (rx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            log("▶ ask battery (008)")
            if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(rx, bytes, type) == 0
            else @Suppress("DEPRECATION") run { rx.writeType = type; rx.value = bytes; g.writeCharacteristic(rx) }
        }
        timers.postDelayed({ askLoop() }, 3_000)
    }

    private fun disconnect(why: String?) {
        asking = false
        stopScan?.invoke()
        if (::scanBtn.isInitialized) scanBtn.isEnabled = true
        timers.removeCallbacksAndMessages(null)
        synchronized(ops) { ops.clear() }
        opBusy = false
        gatt?.let { g ->
            why?.let { log("disconnect: $it") }
            runCatching { g.disconnect() }; runCatching { g.close() }
        }
        gatt = null
        uartRx = null
        if (::stopBtn.isInitialized) { stopBtn.isEnabled = false; askBtn.isEnabled = false; askBtn.setText(R.string.ble_survey_ask_off) }
    }

    // ------------------------------------------------------------------ log + share

    private fun log(s: String) {
        val t = (SystemClock.elapsedRealtime() - t0) / 1000.0
        val pke = PkeBattery.code(this)
        val line = "%7.2f  %s".format(t, if (pke.length >= 8) PkeBattery.masked(s, pke) else s)
        main.post {
            lines.add(line)
            if (lines.size > MAX_LINES) lines.subList(0, lines.size - MAX_LINES).clear()
            logView.append(line + "\n")
            if (logView.length() > 200_000) logView.text = lines.takeLast(800).joinToString("\n", postfix = "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun share() {
        val body = lines.joinToString("\n")
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "RideScreen BLE survey")
                putExtra(Intent.EXTRA_TEXT, body)
            }, getString(R.string.ble_survey_share)))
        } catch (_: Exception) { toast(R.string.open_failed) }
    }

    // ------------------------------------------------------------------ small helpers

    private fun text(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s; textSize = size; setTextColor(ContextCompat.getColor(context, color))
        setPadding(0, ScreenUi.dp(context, 6), 0, ScreenUi.dp(context, 6))
    }

    private fun button(res: Int, label: String? = null, onClick: () -> Unit) =
        com.google.android.material.button.MaterialButton(this).apply {
            text = label ?: getString(res)
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    companion object {
        private val UART_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAX_LINES = 20_000

        fun start(ctx: Context) = ctx.startActivity(Intent(ctx, BleSurveyActivity::class.java))

        /** Last two bytes only: enough to tell devices apart in a shared log. */
        internal fun mac(a: String) = "…" + a.takeLast(5)

        internal fun short(u: UUID) = u.toString().take(8)

        internal fun props(p: Int) = buildList {
            if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
            if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
            if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
            if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
            if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
        }.joinToString("|")

        /** Hex plus printable ASCII, so text frames (`*AT,…#`) and binary ones both read. */
        internal fun fmt(v: ByteArray): String {
            val hex = v.joinToString(" ") { "%02X".format(it) }
            val asc = String(CharArray(v.size) { val b = v[it].toInt() and 0xFF; if (b in 32..126) b.toChar() else '.' })
            return "$hex  |$asc|"
        }
    }
}
