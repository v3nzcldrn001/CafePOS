package com.example.cafepos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.app.DatePickerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DashboardBrown = Color(0xFF4E342E)
private val DashboardCream = Color(0xFFF7F4F0)
private val DashboardGreen = Color(0xFF2E7D32)
private val DashboardRed = Color(0xFFC62828)

/** Standalone sales dashboard. Uses the existing SQLite ShiftDatabase. */
@Composable
fun SalesDashboardScreen(onBack: () -> Unit, printerManager: BluetoothPrinterManager) {
    val context = LocalContext.current
    val database = remember { ShiftDatabase(context) }

    var activeShift by remember { mutableStateOf(database.getActiveShift()) }
    var selectedDate by remember { mutableStateOf(activeShift?.businessDate ?: ShiftDatabase.todayBusinessDate()) }
    var selectedDateShifts by remember { mutableStateOf(database.getShiftsForDate(selectedDate)) }
    var selectedDateSales by remember { mutableStateOf(database.getSalesForDate(selectedDate)) }
    var summary by remember {
        mutableStateOf(database.getDailySummary(activeShift?.businessDate ?: ShiftDatabase.todayBusinessDate()))
    }
    var shifts by remember { mutableStateOf(database.getRecentShifts(50)) }
    var sales by remember { mutableStateOf(database.getRecentSales(50)) }
    var showStartDialog by remember { mutableStateOf(false) }
    var showEndDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var selectedReceipt by remember { mutableStateOf<ReceiptData?>(null) }
    var selectedSaleForActions by remember { mutableStateOf<SaleRecord?>(null) }
    var saleToVoid by remember { mutableStateOf<SaleRecord?>(null) }
    var voidReason by remember { mutableStateOf("") }

    fun refresh() {
        activeShift = database.getActiveShift()
        summary = database.getDailySummary(selectedDate)
        shifts = database.getRecentShifts(50)
        sales = database.getRecentSales(50)
        selectedDateShifts = database.getShiftsForDate(selectedDate)
        selectedDateSales = database.getSalesForDate(selectedDate)
    }

    fun chooseDate() {
        val parts = selectedDate.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        DatePickerDialog(context, { _, y, m, d ->
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            summary = database.getDailySummary(selectedDate)
            selectedDateShifts = database.getShiftsForDate(selectedDate)
            selectedDateSales = database.getSalesForDate(selectedDate)
            message = "Showing saved records for $selectedDate."
        }, year, month, day).show()
    }

    Surface(Modifier.fillMaxSize(), color = DashboardCream) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onBack) { Text("← POS") }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("CATSPRESSO", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DashboardBrown)
                    Text("SALES DASHBOARD", fontSize = 13.sp, color = Color.Gray)
                }
                OutlinedButton(onClick = { chooseDate() }) { Text("DATE: $selectedDate") }
                Button(onClick = {
                    val ok = printDailySalesReport(printerManager, selectedDate, selectedDateSales, selectedDateShifts)
                    message = if (ok) "Daily report sent to MTP-II." else "Could not print daily report. Check printer connection."
                }) { Text("PRINT DAILY") }
                OutlinedButton(onClick = { refresh(); message = "Dashboard refreshed." }) {
                    Text("REFRESH")
                }
            }

            Spacer(Modifier.height(14.dp))
            if (message.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Text(message, Modifier.padding(10.dp), color = DashboardGreen, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("NET SALES • $selectedDate", formatPeso(summary.totalSales), Modifier.weight(1f))
                MetricCard("TRANSACTIONS", summary.transactionCount.toString(), Modifier.weight(1f))
                MetricCard("DISCOUNTS", formatPeso(summary.totalDiscount), Modifier.weight(1f))
                MetricCard("SHIFTS", summary.shiftsCount.toString(), Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("SHIFT STATUS", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        if (activeShift == null) {
                            Text("No active shift", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DashboardRed)
                            Text("Start a shift before accepting sales.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            Text(activeShift!!.shiftName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DashboardBrown)
                            Text("Started: ${activeShift!!.startedAt}", fontSize = 11.sp, color = Color.Gray)
                            Text("Opening cash: ${formatPeso(activeShift!!.openingCash)}", fontSize = 12.sp)
                            Text("Shift sales: ${formatPeso(activeShift!!.salesTotal)} • ${activeShift!!.transactionCount} transaction(s)", fontSize = 12.sp)
                            Text("Expected drawer: ${formatPeso(activeShift!!.openingCash + activeShift!!.salesTotal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (activeShift == null) {
                        Button(onClick = { showStartDialog = true }) { Text("START SHIFT") }
                    } else {
                        Button(onClick = { showEndDialog = true }) { Text("END SHIFT") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.weight(1.1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Text("RECENT SALES", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DashboardBrown)
                        Text("Saved transactions across shifts", fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        if (selectedDateSales.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No sales recorded yet", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(selectedDateSales, key = { it.receiptNumber }) { sale ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedSaleForActions = sale
                                                selectedReceipt = database.getSaleDetails(sale.receiptNumber)
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (sale.isVoided) Color(0xFFFFEBEE) else DashboardCream
                                        )
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(9.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(sale.receiptNumber, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                Text(
                                                    if (sale.isVoided) "VOIDED" else formatPeso(sale.total),
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (sale.isVoided) DashboardRed else DashboardBrown
                                                )
                                            }
                                            Text("${sale.dateTime} • Shift #${sale.shiftId} • ${sale.orderType}", fontSize = 10.sp, color = Color.Gray)
                                            Text("Discount: ${formatPeso(sale.discountAmount)} • Cash: ${formatPeso(sale.cashReceived)} • Change: ${formatPeso(sale.change)}", fontSize = 10.sp)
                                            if (sale.isVoided) {
                                                Text("Voided: ${sale.voidedAt ?: "Unknown time"}", fontSize = 10.sp, color = DashboardRed)
                                                Text("Reason: ${sale.voidReason ?: "No reason saved"}", fontSize = 10.sp, color = DashboardRed)
                                            } else {
                                                Text("Tap to view receipt and actions", fontSize = 10.sp, color = DashboardGreen)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Card(Modifier.weight(0.9f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Text("SHIFT HISTORY", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DashboardBrown)
                        Text("Morning and evening reports", fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        if (selectedDateShifts.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No shifts recorded yet", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(selectedDateShifts, key = { it.id }) { shift ->
                                    Card(colors = CardDefaults.cardColors(containerColor = DashboardCream)) {
                                        Column(Modifier.fillMaxWidth().padding(9.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(shift.shiftName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                Text(if (shift.isOpen) "OPEN" else "CLOSED", fontSize = 10.sp, color = if (shift.isOpen) DashboardGreen else Color.Gray, fontWeight = FontWeight.Bold)
                                            }
                                            Text("${shift.businessDate} • ${shift.startedAt}", fontSize = 10.sp, color = Color.Gray)
                                            Text("Sales: ${formatPeso(shift.salesTotal)} • ${shift.transactionCount} txn", fontSize = 11.sp)
                                            Button(onClick = {
                                                val shiftSales = database.getSalesForShift(shift.id)
                                                val ok = printShiftSalesReport(printerManager, shift, shiftSales)
                                                message = if (ok) "${shift.shiftName} report sent to MTP-II." else "Could not print shift report. Check printer connection."
                                            }, modifier = Modifier.fillMaxWidth()) { Text("PRINT SHIFT REPORT", fontSize = 10.sp) }
                                            if (!shift.isOpen) {
                                                Text("Expected: ${formatPeso(shift.expectedCash ?: shift.computedExpectedCash)}", fontSize = 10.sp)
                                                Text("Actual: ${formatPeso(shift.actualCash ?: 0.0)}", fontSize = 10.sp)
                                                val variance = shift.variance ?: 0.0
                                                Text(
                                                    (if (variance < 0) "SHORTAGE: " else if (variance > 0) "OVERAGE: " else "BALANCED: ") + formatPeso(kotlin.math.abs(variance)),
                                                    fontSize = 11.sp,
                                                    color = if (variance < 0) DashboardRed else DashboardGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartDialog) {
        StartShiftDialog(
            onDismiss = { showStartDialog = false },
            onStart = { name, openingCash ->
                try {
                    database.startShift(name, openingCash)
                    showStartDialog = false
                    message = "${name} started."
                    refresh()
                } catch (e: Exception) {
                    message = e.message ?: "Could not start shift."
                }
            }
        )
    }

    if (showEndDialog) {
        EndShiftDialog(
            shift = activeShift,
            onDismiss = { showEndDialog = false },
            onEnd = { actualCash ->
                val shift = activeShift
                if (shift == null) {
                    message = "There is no active shift."
                } else {
                    try {
                        val report = database.closeShift(shift.id, actualCash)
                        showEndDialog = false
                        val variance = report.variance ?: 0.0
                        message = "Shift closed. Variance: ${formatPeso(variance)}"
                        refresh()
                    } catch (e: Exception) {
                        message = e.message ?: "Could not close shift."
                    }
                }
            }
        )
    }
    selectedReceipt?.let { receipt ->
        val selectedSale = selectedSaleForActions
        AlertDialog(
            onDismissRequest = {
                selectedReceipt = null
                selectedSaleForActions = null
            },
            title = { Text("RECEIPT • ${receipt.receiptNumber}") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Date: ${receipt.dateTime}", fontSize = 12.sp)
                    Text("Order: ${receipt.orderType}", fontSize = 12.sp)
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(receipt.items) { item ->
                            Column {
                                Text("${item.product.name}${if (item.product.size.isNotBlank()) " (${item.product.size})" else ""}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("${item.quantity} × ${formatPeso(item.product.price)} = ${formatPeso(calculateItemTotal(item))}", fontSize = 12.sp)
                                val discount = calculateItemDiscount(item)
                                if (discount > 0.0) {
                                    Text("Item discount: ${formatPeso(discount)}", fontSize = 11.sp, color = DashboardGreen)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Subtotal: ${formatPeso(receipt.subtotal)}", fontSize = 12.sp)
                    Text("Discount: ${formatPeso(receipt.discountAmount)}", fontSize = 12.sp)
                    Text("TOTAL: ${formatPeso(receipt.total)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Cash: ${formatPeso(receipt.cashReceived)} • Change: ${formatPeso(receipt.change)}", fontSize = 12.sp)
                    if (selectedSale?.isVoided == true) {
                        Text("VOIDED • ${selectedSale.voidedAt ?: ""}", color = DashboardRed, fontWeight = FontWeight.Bold)
                        Text("Reason: ${selectedSale.voidReason ?: "No reason saved"}", color = DashboardRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectedSale?.isVoided != true) {
                        TextButton(onClick = {
                            saleToVoid = selectedSale
                            voidReason = ""
                            selectedReceipt = null
                        }) { Text("VOID SALE", color = DashboardRed) }
                    }
                    Button(
                        onClick = {
                            val ok = printActualReceipt(printerManager, receipt)
                            message = if (ok) {
                                "Receipt ${receipt.receiptNumber} sent to MTP-II."
                            } else {
                                "Could not print receipt. Check the printer connection."
                            }
                            selectedReceipt = null
                            selectedSaleForActions = null
                        }
                    ) { Text("REPRINT") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedReceipt = null
                    selectedSaleForActions = null
                }) { Text("CLOSE") }
            }
        )
    }

    saleToVoid?.let { sale ->
        AlertDialog(
            onDismissRequest = { saleToVoid = null; voidReason = "" },
            title = { Text("VOID SALE?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Receipt: ${sale.receiptNumber}")
                    Text("Amount: ${formatPeso(sale.total)}")
                    Text("This will keep the transaction in history but exclude it from net sales and expected cash.")
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("Reason for voiding *") },
                        placeholder = { Text("e.g. Wrong order entered") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = voidReason.isNotBlank() && !sale.isVoided,
                    colors = ButtonDefaults.buttonColors(containerColor = DashboardRed),
                    onClick = {
                        try {
                            val voided = database.voidSale(sale.receiptNumber, voidReason)
                            message = if (voided) {
                                "Receipt ${sale.receiptNumber} voided and saved to audit history."
                            } else {
                                "This receipt was already voided or could not be updated."
                            }
                            saleToVoid = null
                            voidReason = ""
                            refresh()
                        } catch (e: Exception) {
                            message = e.message ?: "Could not void this sale."
                        }
                    }
                ) { Text("CONFIRM VOID") }
            },
            dismissButton = {
                TextButton(onClick = { saleToVoid = null; voidReason = "" }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = DashboardBrown)
        }
    }
}

@Composable
private fun StartShiftDialog(onDismiss: () -> Unit, onStart: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("Morning Shift") }
    var openingCashText by remember { mutableStateOf("0") }
    val openingCash = openingCashText.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("START SHIFT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose a shift label and enter the starting cash in the drawer.", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Morning Shift", "Evening Shift", "Other Shift").forEach { option ->
                        OutlinedButton(onClick = { name = option }, modifier = Modifier.weight(1f)) {
                            Text(option, fontSize = 10.sp)
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shift name") }, singleLine = true)
                OutlinedTextField(
                    value = openingCashText,
                    onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) openingCashText = v },
                    label = { Text("Opening cash (₱)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = { Button(enabled = openingCash != null && openingCash >= 0 && name.isNotBlank(), onClick = { onStart(name.trim(), openingCash!!) }) { Text("START") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun EndShiftDialog(shift: ShiftRecord?, onDismiss: () -> Unit, onEnd: (Double) -> Unit) {
    var actualCashText by remember(shift?.id) { mutableStateOf("") }
    val actualCash = actualCashText.toDoubleOrNull()
    val expected = shift?.let { it.openingCash + it.salesTotal } ?: 0.0
    val variance = (actualCash ?: 0.0) - expected
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("END SHIFT • CASH COUNT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Shift: ${shift?.shiftName ?: "No active shift"}", fontWeight = FontWeight.Bold)
                Text("Opening cash: ${formatPeso(shift?.openingCash ?: 0.0)}")
                Text("Net sales: ${formatPeso(shift?.salesTotal ?: 0.0)}")
                Text("Expected cash: ${formatPeso(expected)}", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = actualCashText,
                    onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) actualCashText = v },
                    label = { Text("Actual cash counted (₱) *") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (actualCash != null && actualCash >= 0) {
                    Text(
                        (if (variance < 0) "SHORTAGE: " else if (variance > 0) "OVERAGE: " else "BALANCED: ") + formatPeso(kotlin.math.abs(variance)),
                        color = if (variance < 0) DashboardRed else DashboardGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("You must enter the actual cash count to close this shift.", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = { Button(enabled = actualCash != null && actualCash >= 0 && shift != null, onClick = { onEnd(actualCash!!) }) { Text("CONFIRM END SHIFT") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}


fun printShiftSalesReport(printer: BluetoothPrinterManager, shift: ShiftRecord, sales: List<SaleRecord>): Boolean {
    if (!printer.isConnected()) return false
    return try {
        printer.initializePrinter()
        printer.setLineSpacing(24)
        printer.alignCenter()
        printer.boldOn(); printer.printText("CATSPRESSO\n"); printer.boldOff()
        printer.printText("SHIFT SALES REPORT\n${shift.businessDate}\n")
        printer.alignLeft()
        printer.printText("--------------------------------\n")
        printer.printText("Shift: ${shift.shiftName}\nStarted: ${shift.startedAt}\n")
        printer.printText("Status: ${if (shift.isOpen) "OPEN" else "CLOSED"}\n")
        printer.printText("Transactions: ${shift.transactionCount}\n")
        printer.printText("--------------------------------\n")
        printer.printText(thermalTotalLine("Gross subtotal", sales.filter { !it.isVoided }.sumOf { it.subtotal }))
        printer.printText(thermalTotalLine("Discounts", sales.filter { !it.isVoided }.sumOf { it.discountAmount }))
        printer.boldOn(); printer.printText(thermalTotalLine("NET SALES", sales.filter { !it.isVoided }.sumOf { it.total })); printer.boldOff()
        printer.printText("--------------------------------\n")
        printer.printText(thermalTotalLine("Opening cash", shift.openingCash))
        printer.printText(thermalTotalLine("Expected cash", shift.expectedCash ?: shift.computedExpectedCash))
        if (shift.actualCash != null) printer.printText(thermalTotalLine("Actual cash", shift.actualCash))
        shift.variance?.let { printer.printText(thermalTotalLine(if (it < 0) "SHORT" else if (it > 0) "OVER" else "BALANCED", kotlin.math.abs(it))) }
        printer.printText("--------------------------------\n")
        sales.forEach { sale ->
            printer.printText("${sale.dateTime}\n")
            printer.printText(thermalItemLine(sale.receiptNumber, "PHP${formatThermalAmount(sale.total)}"))
        }
        printer.alignCenter(); printer.boldOn(); printer.printText("END OF SHIFT REPORT\n"); printer.boldOff()
        printer.feed(3); printer.cutPaper(); true
    } catch (_: Exception) { false }
}

fun printDailySalesReport(printer: BluetoothPrinterManager, date: String, sales: List<SaleRecord>, shifts: List<ShiftRecord>): Boolean {
    if (!printer.isConnected()) return false
    return try {
        printer.initializePrinter()
        printer.setLineSpacing(24)
        printer.alignCenter()
        printer.boldOn(); printer.printText("CATSPRESSO\n"); printer.boldOff()
        printer.printText("DAILY SALES REPORT\n$date\n")
        printer.alignLeft()
        printer.printText("--------------------------------\n")
        printer.printText(thermalTotalLine("Shifts", shifts.size.toDouble()))
        printer.printText(thermalTotalLine("Transactions", sales.size.toDouble()))
        printer.printText(thermalTotalLine("Gross subtotal", sales.filter { !it.isVoided }.sumOf { it.subtotal }))
        printer.printText(thermalTotalLine("Discounts", sales.filter { !it.isVoided }.sumOf { it.discountAmount }))
        printer.boldOn(); printer.printText(thermalTotalLine("NET SALES", sales.filter { !it.isVoided }.sumOf { it.total })); printer.boldOff()
        printer.printText("--------------------------------\n")
        printer.boldOn(); printer.printText("SHIFT BREAKDOWN\n"); printer.boldOff()
        shifts.forEach { shift ->
            printer.printText("${shift.shiftName} ${if (shift.isOpen) "(OPEN)" else "(CLOSED)"}\n")
            printer.printText(thermalTotalLine("Sales", shift.salesTotal))
            printer.printText(thermalTotalLine("Transactions", shift.transactionCount.toDouble()))
            if (!shift.isOpen) {
                printer.printText(thermalTotalLine("Expected cash", shift.expectedCash ?: shift.computedExpectedCash))
                printer.printText(thermalTotalLine("Actual cash", shift.actualCash ?: 0.0))
                printer.printText(thermalTotalLine("Over/Short", shift.variance ?: 0.0))
            }
            printer.printText("--------------------------------\n")
        }
        printer.boldOn(); printer.alignCenter(); printer.printText("END OF DAILY REPORT\n"); printer.boldOff()
        printer.feed(3); printer.cutPaper(); true
    } catch (_: Exception) { false }
}