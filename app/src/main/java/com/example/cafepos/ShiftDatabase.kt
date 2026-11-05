package com.example.cafepos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persistent local database for CATSPRESSO sales and cashier shifts. */
class ShiftDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE shifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                business_date TEXT NOT NULL,
                shift_name TEXT NOT NULL,
                started_at TEXT NOT NULL,
                opening_cash REAL NOT NULL DEFAULT 0,
                closed_at TEXT,
                expected_cash REAL,
                actual_cash REAL,
                variance REAL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE sales (
                receipt_number TEXT PRIMARY KEY,
                shift_id INTEGER NOT NULL,
                business_date TEXT NOT NULL,
                date_time TEXT NOT NULL,
                items_json TEXT NOT NULL,
                subtotal REAL NOT NULL,
                discount_amount REAL NOT NULL,
                total REAL NOT NULL,
                cash_received REAL NOT NULL,
                change_amount REAL NOT NULL,
                order_type TEXT NOT NULL,
                is_voided INTEGER NOT NULL DEFAULT 0,
                voided_at TEXT,
                void_reason TEXT,
                FOREIGN KEY(shift_id) REFERENCES shifts(id)
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sales_business_date ON sales(business_date)")
        db.execSQL("CREATE INDEX idx_sales_shift_id ON sales(shift_id)")
        db.execSQL("CREATE INDEX idx_shifts_business_date ON shifts(business_date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive migration only: preserve every saved sale and shift.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sales ADD COLUMN is_voided INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sales ADD COLUMN voided_at TEXT")
            db.execSQL("ALTER TABLE sales ADD COLUMN void_reason TEXT")
        }
    }

    @Synchronized
    fun getActiveShift(): ShiftRecord? {
        val db = readableDatabase
        db.rawQuery(
            """SELECT s.id, s.business_date, s.shift_name, s.started_at, s.opening_cash,
                s.closed_at, s.expected_cash, s.actual_cash, s.variance,
                COALESCE(SUM(CASE WHEN a.is_voided = 0 THEN a.total ELSE 0 END), 0), COUNT(CASE WHEN a.is_voided = 0 THEN a.receipt_number END)
                FROM shifts s LEFT JOIN sales a ON a.shift_id = s.id
                WHERE s.closed_at IS NULL GROUP BY s.id ORDER BY s.id DESC LIMIT 1""",
            null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return c.toShiftRecord()
        }
    }

    @Synchronized
    fun startShift(name: String, openingCash: Double): Long {
        require(openingCash >= 0.0) { "Opening cash cannot be negative." }
        check(getActiveShift() == null) { "There is already an open shift." }
        val values = ContentValues().apply {
            put("business_date", todayBusinessDate())
            put("shift_name", name.trim().ifBlank { "General Shift" })
            put("started_at", nowDateTime())
            put("opening_cash", openingCash)
        }
        return writableDatabase.insertOrThrow("shifts", null, values)
    }

    @Synchronized
    fun saveSale(receipt: ReceiptData, shiftId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val shiftCheck = db.rawQuery(
                "SELECT id FROM shifts WHERE id = ? AND closed_at IS NULL",
                arrayOf(shiftId.toString())
            ).use { it.moveToFirst() }
            check(shiftCheck) { "No active shift. Start a shift before recording a sale." }

            val shiftBusinessDate = db.rawQuery(
                "SELECT business_date FROM shifts WHERE id = ?",
                arrayOf(shiftId.toString())
            ).use { c ->
                check(c.moveToFirst()) { "Shift not found." }
                c.getString(0)
            }
            val values = ContentValues().apply {
                put("receipt_number", receipt.receiptNumber)
                put("shift_id", shiftId)
                // Sales belong to the shift's business date, even if the shift crosses midnight.
                put("business_date", shiftBusinessDate)
                put("date_time", receipt.dateTime)
                put("items_json", itemsToJson(receipt.items).toString())
                put("subtotal", receipt.subtotal)
                put("discount_amount", receipt.discountAmount)
                put("total", receipt.total)
                put("cash_received", receipt.cashReceived)
                put("change_amount", receipt.change)
                put("order_type", receipt.orderType.name)
            }
            db.insertOrThrow("sales", null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun closeShift(shiftId: Long, actualCash: Double): ShiftRecord {
        require(actualCash >= 0.0) { "Actual cash cannot be negative." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val openingCash = db.rawQuery(
                "SELECT opening_cash FROM shifts WHERE id = ? AND closed_at IS NULL",
                arrayOf(shiftId.toString())
            ).use { c ->
                check(c.moveToFirst()) { "The shift is already closed or does not exist." }
                c.getDouble(0)
            }
            val salesTotal = db.rawQuery(
                "SELECT COALESCE(SUM(total), 0) FROM sales WHERE shift_id = ? AND is_voided = 0",
                arrayOf(shiftId.toString())
            ).use { c -> c.moveToFirst(); c.getDouble(0) }
            val expectedCash = openingCash + salesTotal
            val variance = actualCash - expectedCash
            val values = ContentValues().apply {
                put("closed_at", nowDateTime())
                put("expected_cash", expectedCash)
                put("actual_cash", actualCash)
                put("variance", variance)
            }
            val changed = db.update("shifts", values, "id = ? AND closed_at IS NULL", arrayOf(shiftId.toString()))
            check(changed == 1) { "Could not close this shift." }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return getShift(shiftId) ?: error("Shift report could not be loaded after closing.")
    }

    @Synchronized
    fun getShift(shiftId: Long): ShiftRecord? {
        return readableDatabase.rawQuery(
            """
            SELECT
                s.id,
                s.business_date,
                s.shift_name,
                s.started_at,
                s.opening_cash,
                s.closed_at,
                s.expected_cash,
                s.actual_cash,
                s.variance,
                COALESCE(SUM(a.total), 0),
                COUNT(a.receipt_number)
            FROM shifts s
            LEFT JOIN sales a ON a.shift_id = s.id
            WHERE s.id = ?
            GROUP BY s.id
            LIMIT 1
            """.trimIndent(),
            arrayOf(shiftId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toShiftRecord()
            } else {
                null
            }
        }
    }

    @Synchronized
    fun getDailySummary(date: String = todayBusinessDate()): DailySalesSummary {
        val salesValues = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(total), 0), COALESCE(SUM(discount_amount), 0), COUNT(*) FROM sales WHERE business_date = ? AND is_voided = 0",
            arrayOf(date)
        ).use { c ->
            c.moveToFirst()
            Triple(c.getDouble(0), c.getDouble(1), c.getInt(2))
        }
        val shiftsCount = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM shifts WHERE business_date = ?",
            arrayOf(date)
        ).use { c -> c.moveToFirst(); c.getInt(0) }
        return DailySalesSummary(date, salesValues.first, salesValues.second, salesValues.third, shiftsCount)
    }

    @Synchronized
    fun getShiftsForDate(date: String): List<ShiftRecord> {
        val results = mutableListOf<ShiftRecord>()
        readableDatabase.rawQuery(
            """SELECT s.id, s.business_date, s.shift_name, s.started_at, s.opening_cash,
                s.closed_at, s.expected_cash, s.actual_cash, s.variance,
                COALESCE(SUM(CASE WHEN a.is_voided = 0 THEN a.total ELSE 0 END), 0), COUNT(CASE WHEN a.is_voided = 0 THEN a.receipt_number END)
                FROM shifts s LEFT JOIN sales a ON a.shift_id = s.id
                WHERE s.business_date = ? GROUP BY s.id ORDER BY s.id ASC""",
            arrayOf(date)
        ).use { c -> while (c.moveToNext()) results.add(c.toShiftRecord()) }
        return results
    }

    @Synchronized
    fun getSalesForDate(date: String): List<SaleRecord> = getSalesWhere("business_date = ?", arrayOf(date))

    @Synchronized
    fun getSalesForShift(shiftId: Long): List<SaleRecord> = getSalesWhere("shift_id = ?", arrayOf(shiftId.toString()))

    private fun getSalesWhere(selection: String, args: Array<String>): List<SaleRecord> {
        val results = mutableListOf<SaleRecord>()
        readableDatabase.rawQuery(
            """SELECT receipt_number, shift_id, business_date, date_time, subtotal, discount_amount,
                total, cash_received, change_amount, order_type, items_json, is_voided, voided_at, void_reason
                FROM sales WHERE $selection ORDER BY rowid ASC""",
            args
        ).use { c ->
            while (c.moveToNext()) results.add(SaleRecord(
                receiptNumber = c.getString(0), shiftId = c.getLong(1), businessDate = c.getString(2),
                dateTime = c.getString(3), subtotal = c.getDouble(4), discountAmount = c.getDouble(5),
                total = c.getDouble(6), cashReceived = c.getDouble(7), change = c.getDouble(8),
                orderType = c.getString(9), itemsJson = c.getString(10),
                isVoided = c.getInt(11) == 1,
                voidedAt = if (c.isNull(12)) null else c.getString(12),
                voidReason = if (c.isNull(13)) null else c.getString(13)
            ))
        }
        return results
    }

    @Synchronized
    fun getRecentShifts(limit: Int = 20): List<ShiftRecord> {
        val results = mutableListOf<ShiftRecord>()
        readableDatabase.rawQuery(
            """SELECT s.id, s.business_date, s.shift_name, s.started_at, s.opening_cash,
                s.closed_at, s.expected_cash, s.actual_cash, s.variance,
                COALESCE(SUM(CASE WHEN a.is_voided = 0 THEN a.total ELSE 0 END), 0), COUNT(CASE WHEN a.is_voided = 0 THEN a.receipt_number END)
                FROM shifts s LEFT JOIN sales a ON a.shift_id = s.id
                GROUP BY s.id ORDER BY s.id DESC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { c -> while (c.moveToNext()) results.add(c.toShiftRecord()) }
        return results
    }

    @Synchronized
    fun getRecentSales(limit: Int = 30): List<SaleRecord> {
        val results = mutableListOf<SaleRecord>()
        readableDatabase.rawQuery(
            """SELECT receipt_number, shift_id, business_date, date_time, subtotal, discount_amount,
                total, cash_received, change_amount, order_type, items_json, is_voided, voided_at, void_reason
                FROM sales ORDER BY rowid DESC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                results.add(
                    SaleRecord(
                        receiptNumber = c.getString(0), shiftId = c.getLong(1), businessDate = c.getString(2),
                        dateTime = c.getString(3), subtotal = c.getDouble(4), discountAmount = c.getDouble(5),
                        total = c.getDouble(6), cashReceived = c.getDouble(7), change = c.getDouble(8),
                        orderType = c.getString(9), itemsJson = c.getString(10),
                        isVoided = c.getInt(11) == 1,
                        voidedAt = if (c.isNull(12)) null else c.getString(12),
                        voidReason = if (c.isNull(13)) null else c.getString(13)
                    )
                )
            }
        }
        return results
    }

    /** Voids a saved sale without deleting it. The reason and timestamp remain in the audit trail. */
    @Synchronized
    fun voidSale(receiptNumber: String, reason: String): Boolean {
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "Please provide a reason for voiding this sale." }
        val values = ContentValues().apply {
            put("is_voided", 1)
            put("voided_at", nowDateTime())
            put("void_reason", cleanReason)
        }
        return writableDatabase.update(
            "sales",
            values,
            "receipt_number = ? AND is_voided = 0",
            arrayOf(receiptNumber)
        ) == 1
    }

    /** Rebuilds the original receipt from saved database values; does not print anything. */
    @Synchronized
    fun getSaleDetails(receiptNumber: String): ReceiptData? {
        return readableDatabase.rawQuery(
            """SELECT receipt_number, date_time, items_json, subtotal, discount_amount,
                total, cash_received, change_amount, order_type
                FROM sales WHERE receipt_number = ? LIMIT 1""",
            arrayOf(receiptNumber)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val orderType = runCatching {
                OrderType.valueOf(c.getString(8))
            }.getOrDefault(OrderType.TAKE_OUT)
            ReceiptData(
                receiptNumber = c.getString(0),
                items = itemsFromJson(c.getString(2)),
                subtotal = c.getDouble(3),
                discountAmount = c.getDouble(4),
                total = c.getDouble(5),
                cashReceived = c.getDouble(6),
                change = c.getDouble(7),
                orderType = orderType,
                dateTime = c.getString(1)
            )
        }
    }

    private fun itemsFromJson(json: String): List<CartItem> {
        val array = JSONArray(json)
        val result = mutableListOf<CartItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val discountType = runCatching {
                DiscountType.valueOf(obj.optString("discountType", "NONE"))
            }.getOrDefault(DiscountType.NONE)
            result.add(
                CartItem(
                    product = Product(
                        id = obj.optInt("productId"),
                        name = obj.optString("name", "Unknown item"),
                        category = obj.optString("category", ""),
                        size = obj.optString("size", ""),
                        price = obj.optDouble("price", 0.0)
                    ),
                    quantity = obj.optInt("quantity", 1),
                    discountedQuantity = obj.optInt("discountedQuantity", 0),
                    discountType = discountType,
                    manualDiscountPercent = obj.optDouble("manualDiscountPercent", 0.0)
                )
            )
        }
        return result
    }

    private fun itemsToJson(items: List<CartItem>): JSONArray = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("productId", item.product.id)
                put("name", item.product.name)
                put("category", item.product.category)
                put("size", item.product.size)
                put("price", item.product.price)
                put("quantity", item.quantity)
                put("discountedQuantity", item.discountedQuantity)
                put("discountType", item.discountType.name)
                put("manualDiscountPercent", item.manualDiscountPercent)
                put("lineDiscount", calculateItemDiscount(item))
                put("lineTotal", calculateItemTotal(item))
            })
        }
    }

    private fun android.database.Cursor.toShiftRecord(): ShiftRecord = ShiftRecord(
        id = getLong(0), businessDate = getString(1), shiftName = getString(2), startedAt = getString(3),
        openingCash = getDouble(4), closedAt = if (isNull(5)) null else getString(5),
        expectedCash = if (isNull(6)) null else getDouble(6), actualCash = if (isNull(7)) null else getDouble(7),
        variance = if (isNull(8)) null else getDouble(8), salesTotal = getDouble(9), transactionCount = getInt(10)
    )

    companion object {
        private const val DATABASE_NAME = "catspresso_sales.db"
        private const val DATABASE_VERSION = 2
        fun todayBusinessDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        fun nowDateTime(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}

data class ShiftRecord(
    val id: Long,
    val businessDate: String,
    val shiftName: String,
    val startedAt: String,
    val openingCash: Double,
    val closedAt: String?,
    val expectedCash: Double?,
    val actualCash: Double?,
    val variance: Double?,
    val salesTotal: Double,
    val transactionCount: Int
) {
    val isOpen: Boolean get() = closedAt == null
    val computedExpectedCash: Double get() = openingCash + salesTotal
}

data class DailySalesSummary(
    val businessDate: String,
    val totalSales: Double,
    val totalDiscount: Double,
    val transactionCount: Int,
    val shiftsCount: Int
)

data class SaleRecord(
    val receiptNumber: String,
    val shiftId: Long,
    val businessDate: String,
    val dateTime: String,
    val subtotal: Double,
    val discountAmount: Double,
    val total: Double,
    val cashReceived: Double,
    val change: Double,
    val orderType: String = OrderType.TAKE_OUT.name,
    val itemsJson: String = "[]",
    val isVoided: Boolean = false,
    val voidedAt: String? = null,
    val voidReason: String? = null
)
