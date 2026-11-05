package com.example.cafepos

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothPrinterManager(
    private val context: Context
) {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    companion object {

        // Bluetooth Serial Port Profile UUID
        private val SPP_UUID: UUID =
            UUID.fromString(
                "00001101-0000-1000-8000-00805F9B34FB"
            )
    }

    // =========================================================
    // BLUETOOTH ADAPTER
    // =========================================================

    private fun adapter(): BluetoothAdapter? {
        return BluetoothAdapter.getDefaultAdapter()
    }

    // =========================================================
    // CONNECTION STATUS
    // =========================================================

    fun isConnected(): Boolean {

        return bluetoothSocket?.isConnected == true &&
                outputStream != null
    }

    // =========================================================
    // GET PAIRED DEVICES
    // =========================================================

    fun getPairedDevices(): List<BluetoothDevice> {

        val bluetoothAdapter =
            adapter()
                ?: return emptyList()

        return try {

            bluetoothAdapter
                .bondedDevices
                .toList()
                .sortedBy {
                    it.name ?: ""
                }

        } catch (
            _: SecurityException
        ) {

            emptyList()
        }
    }

    // =========================================================
    // CONNECT
    // =========================================================

    fun connect(
        device: BluetoothDevice
    ): Boolean {

        disconnect()

        return try {

            val bluetoothAdapter =
                adapter()

            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (
                _: Exception
            ) {
            }

            val socket =
                device.createRfcommSocketToServiceRecord(
                    SPP_UUID
                )

            socket.connect()

            bluetoothSocket =
                socket

            outputStream =
                socket.outputStream

            initializePrinter()

            true

        } catch (
            _: Exception
        ) {

            disconnect()

            false
        }
    }

    // =========================================================
    // CONNECT BY NAME
    // =========================================================

    fun connectByName(
        name: String
    ): Boolean {

        val device =
            getPairedDevices()
                .firstOrNull {
                    it.name.equals(
                        name,
                        ignoreCase = true
                    )
                }
                ?: return false

        return connect(device)
    }

    // =========================================================
    // INITIALIZE PRINTER
    // =========================================================

    fun initializePrinter(): Boolean {

        return printRaw(
            byteArrayOf(
                0x1B,
                0x40
            )
        )
    }

    // =========================================================
    // LINE SPACING
    // =========================================================

    fun setLineSpacing(
        spacing: Int = 24
    ): Boolean {

        val safeSpacing =
            spacing.coerceIn(
                16,
                40
            )

        return printRaw(
            byteArrayOf(
                0x1B,
                0x33,
                safeSpacing.toByte()
            )
        )
    }

    // =========================================================
    // ALIGN LEFT
    // =========================================================

    fun alignLeft(): Boolean {

        return printRaw(
            byteArrayOf(
                0x1B,
                0x61,
                0x00
            )
        )
    }

    // =========================================================
    // ALIGN CENTER
    // =========================================================

    fun alignCenter(): Boolean {

        return printRaw(
            byteArrayOf(
                0x1B,
                0x61,
                0x01
            )
        )
    }

    // =========================================================
    // ALIGN RIGHT
    // =========================================================

    fun alignRight(): Boolean {

        return printRaw(
            byteArrayOf(
                0x1B,
                0x61,
                0x02
            )
        )
    }

    // =========================================================
    // BOLD ON
    // =========================================================

    fun boldOn(): Boolean {

        return printRaw(
            byteArrayOf(
                0x1B,
                0x45,
                0x01
            )
        )
    }

    // =========================================================
    // BOLD OFF
    // =========================================================

    fun boldOff(): Boolean {

        return printRaw(
            byteArrayOf(
                0x1B,
                0x45,
                0x00
            )
        )
    }

    // =========================================================
    // PRINT TEXT
    // =========================================================

    fun printText(
        text: String
    ): Boolean {

        /*
         * We intentionally use ASCII here.
         *
         * This prevents the MTP-II from printing corrupted
         * characters when using the peso sign.
         *
         * Physical receipt therefore uses:
         *
         * PHP 100.00
         *
         * instead of:
         *
         * ₱100.00
         */

        val bytes =
            text.toByteArray(
                Charsets.US_ASCII
            )

        return printRaw(bytes)
    }

    // =========================================================
    // FEED PAPER
    // =========================================================

    fun feed(
        lines: Int = 3
    ): Boolean {

        val safeLines =
            lines.coerceIn(
                1,
                10
            )

        return printRaw(
            byteArrayOf(
                0x1B,
                0x64,
                safeLines.toByte()
            )
        )
    }

    // =========================================================
    // CUT PAPER
    // =========================================================

    fun cutPaper(): Boolean {

        return printRaw(
            byteArrayOf(
                0x1D,
                0x56,
                0x00
            )
        )
    }

    // =========================================================
    // RAW PRINT
    // =========================================================

    fun printRaw(
        data: ByteArray
    ): Boolean {

        val stream =
            outputStream
                ?: return false

        return try {

            stream.write(data)
            stream.flush()

            true

        } catch (
            _: IOException
        ) {

            disconnect()

            false

        } catch (
            _: Exception
        ) {

            disconnect()

            false
        }
    }

    // =========================================================
    // TEST PRINT
    // =========================================================

    fun printTestPage(): Boolean {

        if (!isConnected()) {
            return false
        }

        return try {

            initializePrinter()

            setLineSpacing(
                24
            )

            alignCenter()

            boldOn()

            printText(
                "CATSPRESSO\n"
            )

            boldOff()

            printText(
                "Coffee & More\n"
            )

            printText(
                "--------------------------------\n"
            )

            printText(
                "MTP-II TEST PRINT\n"
            )

            printText(
                "--------------------------------\n"
            )

            alignLeft()

            printText(
                "Printer: MTP-II\n"
            )

            printText(
                "Interface: Bluetooth\n"
            )

            printText(
                "Status: OK\n"
            )

            printText(
                "--------------------------------\n"
            )

            alignCenter()

            printText(
                "Thank you!\n"
            )

            feed(
                3
            )

            cutPaper()

            true

        } catch (
            _: Exception
        ) {

            false
        }
    }

    // =========================================================
    // DISCONNECT
    // =========================================================

    fun disconnect() {

        try {

            outputStream?.close()

        } catch (
            _: Exception
        ) {
        }

        try {

            bluetoothSocket?.close()

        } catch (
            _: Exception
        ) {
        }

        outputStream = null
        bluetoothSocket = null
    }
}