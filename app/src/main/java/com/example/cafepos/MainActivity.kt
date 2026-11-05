package com.example.cafepos

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cafepos.ui.theme.CafePOSTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CoffeeBrown = Color(0xFF4E342E)
private val CoffeeAccent = Color(0xFF6D4C41)
private val PaleBackground = Color(0xFFF7F4F0)
private val SuccessGreen = Color(0xFF2E7D32)
private val ErrorRed = Color(0xFFB71C1C)

// ============================= MODELS =============================
data class Product(val id: Int, val name: String, val category: String, val size: String = "", val price: Double = 0.0)
enum class DiscountType { NONE, SENIOR, PWD, MANUAL }
enum class OrderType { DINE_IN, TAKE_OUT }
data class CartItem(
    val product: Product,
    val quantity: Int,
    val discountedQuantity: Int = 0,
    val discountType: DiscountType = DiscountType.NONE,
    val manualDiscountPercent: Double = 0.0
)
data class ReceiptData(
    val receiptNumber: String,
    val items: List<CartItem>,
    val subtotal: Double,
    val discountAmount: Double,
    val total: Double,
    val cashReceived: Double,
    val change: Double,
    val orderType: OrderType,
    val dateTime: String
)

fun calculateItemDiscount(item: CartItem): Double = when (item.discountType) {
    DiscountType.SENIOR, DiscountType.PWD -> item.product.price * item.discountedQuantity * 0.20
    DiscountType.MANUAL -> item.product.price * item.quantity * (item.manualDiscountPercent / 100.0)
    DiscountType.NONE -> 0.0
}
fun calculateItemTotal(item: CartItem): Double =
    (item.product.price * item.quantity - calculateItemDiscount(item)).coerceAtLeast(0.0)
fun formatPeso(amount: Double): String = String.format(Locale.US, "₱%,.2f", amount)
fun formatThermalAmount(amount: Double): String = String.format(Locale.US, "%,.2f", amount)

// ============================= ACTIVITY =============================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()
        setContent { CafePOSTheme { CafePOSApp() } }
    }
}

// ============================= MENU PRICE DATA =============================
data class MenuEntry(val name: String, val category: String, val prices: Map<String, Double> = emptyMap(), val fixedPrice: Double? = null)
private fun entry(name: String, category: String, prices: Map<String, Double>) = MenuEntry(name, category, prices)
private fun fixed(name: String, category: String, price: Double) = MenuEntry(name, category, fixedPrice = price)
private val menuEntries = listOf(
    // HOT COFFEE — 8oz / 12oz
    entry("Spanish Latte -  Hot ", "Hot Coffee", mapOf("8oz" to 100.0, "12oz" to 110.0)),
    entry("Cafe Latte -  Hot ", "Hot Coffee", mapOf("8oz" to 90.0, "12oz" to 100.0)),
    entry("Hazelnut Latte -  Hot ", "Hot Coffee", mapOf("8oz" to 110.0, "12oz" to 120.0)),
    entry("Vanilla Latte -  Hot ", "Hot Coffee", mapOf("8oz" to 110.0, "12oz" to 120.0)),
    entry("Caramel Macchiato -  Hot ", "Hot Coffee", mapOf("8oz" to 115.0, "12oz" to 125.0)),
    entry("Cappuccino -  Hot ", "Hot Coffee", mapOf("8oz" to 80.0, "12oz" to 90.0)),
    entry("Americano -  Hot ", "Hot Coffee", mapOf("8oz" to 80.0, "12oz" to 90.0)),
    entry("Salted Caramel Latte -  Hot ", "Hot Coffee", mapOf("8oz" to 110.0, "12oz" to 120.0)),
    entry("Café Mocha -  Hot ", "Hot Coffee", mapOf("8oz" to 110.0, "12oz" to 120.0)),
    entry("White Choco Mocha Latte -  Hot ", "Hot Coffee", mapOf("8oz" to 110.0, "12oz" to 120.0)),
    entry("Biscoff Latte -  Hot ", "Hot Coffee", mapOf("8oz" to 115.0, "12oz" to 130.0)),
    entry("Pistachio Latte -  Hot ", "Hot Coffee", mapOf("8oz" to 115.0, "12oz" to 130.0)),
    // ICED COFFEE — cold cups only, 12oz / 16oz / 22oz
    entry("Spanish Latte", "Iced Coffee", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Cafe Latte", "Iced Coffee", mapOf("12oz" to 110.0, "16oz" to 120.0, "22oz" to 130.0)),
    entry("Hazelnut Latte", "Iced Coffee", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Vanilla Latte", "Iced Coffee", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Caramel Macchiato", "Iced Coffee", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Americano", "Iced Coffee", mapOf("12oz" to 110.0, "16oz" to 120.0, "22oz" to 130.0)),
    entry("Salted Caramel Latte", "Iced Coffee", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Café Mocha", "Iced Coffee", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("White Choco Mocha Latte", "Iced Coffee", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Biscoff Latte", "Iced Coffee", mapOf("12oz" to 140.0, "16oz" to 150.0, "22oz" to 170.0)),
    entry("Pistachio Latte", "Iced Coffee", mapOf("12oz" to 140.0, "16oz" to 150.0, "22oz" to 170.0)),
    // ICED NON-COFFEE
    entry("Strawberry -  Non-coffee ", "Iced Non-Coffee", mapOf("12oz" to 100.0, "16oz" to 110.0, "22oz" to 120.0)),
    entry("Mango -  Non-coffee ", "Iced Non-Coffee", mapOf("12oz" to 100.0, "16oz" to 110.0, "22oz" to 120.0)),
    entry("Oreo -  Non-coffee ", "Iced Non-Coffee", mapOf("12oz" to 110.0, "16oz" to 120.0, "22oz" to 130.0)),
    entry("Ube -  Non-coffee ", "Iced Non-Coffee", mapOf("12oz" to 110.0, "16oz" to 120.0, "22oz" to 130.0)),
    entry("Biscoff -  Non-coffee ", "Iced Non-Coffee", mapOf("12oz" to 110.0, "16oz" to 120.0, "22oz" to 130.0)),
    entry("Nutella -  Non-coffee ", "Iced Non-Coffee", mapOf("12oz" to 110.0, "16oz" to 120.0, "22oz" to 130.0)),
    // FRAPPE
    entry("Nutella Frappe", "Frappe", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Caramel Frappe", "Frappe", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Ube Frappe", "Frappe", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Biscoff Frappe", "Frappe", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Pistachio Frappe", "Frappe", mapOf("12oz" to 150.0, "16oz" to 160.0, "22oz" to 180.0)),
    entry("Mocha Frappe", "Frappe", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Oreo Frappe", "Frappe", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Coffee Jelly Frappe", "Frappe", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Java Chip Frappe", "Frappe", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Matcha Frappe", "Frappe", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    // SMOOTHIES
    entry("Mango Smoothie", "Smoothies", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Strawberry Smoothie", "Smoothies", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Chocolate Peanut Butter Smoothie", "Smoothies", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Nutty Crunch Smoothie", "Smoothies", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    // MATCHA SERIES
    entry("Classic Matcha", "Matcha Series", mapOf("12oz" to 110.0, "16oz" to 120.0, "22oz" to 140.0)),
    entry("Strawberry Matcha", "Matcha Series", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Mango Matcha", "Matcha Series", mapOf("12oz" to 120.0, "16oz" to 130.0, "22oz" to 150.0)),
    entry("Oreo Matcha", "Matcha Series", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Ube Matcha", "Matcha Series", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    entry("Salted Cream Matcha", "Matcha Series", mapOf("12oz" to 130.0, "16oz" to 140.0, "22oz" to 160.0)),
    // CROFFLES
    fixed("Classic Croffle", "Croffles", 100.0),
    fixed("Hershey's Chocolate Alcapone Croffle", "Croffles", 140.0),
    fixed("Hershey's Strawberry Croffle", "Croffles", 130.0),
    fixed("Hershey's Caramel Croffle", "Croffles", 130.0),
    fixed("Nutella Croffle", "Croffles", 140.0),
    fixed("Biscoff Croffle", "Croffles", 150.0),
    fixed("Oreo Croffle", "Croffles", 150.0),
    // WAFFLES
    fixed("Classic Waffle (No Topping)", "Waffles", 80.0),
    fixed("Chocolate Waffle (No Topping)", "Waffles", 100.0),
    // PASTRIES
    fixed("Ube Ensaymada", "Pastries", 65.0),
    fixed("Chessy Ensaymada", "Pastries", 55.0),
    fixed("Leche Flan Egg Pie", "Pastries", 55.0),
    fixed("Caramel Egg Pie", "Pastries", 55.0),
    fixed("Custard", "Pastries", 55.0),
    fixed("Ube Cheese Bread", "Pastries", 65.0),
    fixed("Queso Cheese Bread", "Pastries", 70.0),
    fixed("Muffin", "Pastries", 70.0),
    fixed("Nutella Muffin", "Pastries", 80.0),
    // NACHOS / FRIES / RICE BOWLS
    fixed("Nachos Overload", "Nachos", 130.0),
    fixed("Classic Fries (Cheese / BBQ / Sour Cream & Onion)", "Fries", 60.0),
    fixed("Overload Fries", "Fries", 100.0),
    fixed("Longganisa with Egg Rice Bowl", "Rice Bowl", 89.0),
    fixed("Siomai with Egg Rice Bowl", "Rice Bowl", 89.0),
    fixed("Hungarian with Egg Rice Bowl", "Rice Bowl", 99.0),
    fixed("Nuggets with Egg Rice Bowl", "Rice Bowl", 89.0),
    fixed("Lumpia with Egg Rice Bowl", "Rice Bowl", 89.0),
    fixed("Luncheon Meat with Egg Rice Bowl", "Rice Bowl", 89.0),
    // RICE BOWL ADD-ONS — prices are per piece / serving
    fixed("Egg 1pcs (Rice Bowl Add-on)", "Rice Bowl Add-ons", 15.0),
    fixed("Rice 1pcs (Rice Bowl Add-on)", "Rice Bowl Add-ons", 20.0),
    fixed("Hungarian 1pcs (Rice Bowl Add-on)", "Rice Bowl Add-ons", 35.0),
    fixed("Longganisa 1pcs (Rice Bowl Add-on)", "Rice Bowl Add-ons", 20.0),
    fixed("Siomai 1pcs (Rice Bowl Add-on)", "Rice Bowl Add-ons", 10.0),
    fixed("Nuggets 1pcs (Rice Bowl Add-on)", "Rice Bowl Add-ons", 12.0),
    fixed("Shanghai 1pcs (Rice Bowl Add-on)", "Rice Bowl Add-ons", 10.0),
    fixed("Luncheon Meat 1pcs (Rice Bowl Add-on)", "Rice Bowl Add-ons", 15.0),
    // DRINK ADD-ONS
    fixed("Espresso Shot", "Drink Add-ons", 25.0),
    fixed("Whipped Cream", "Drink Add-ons", 20.0),
    fixed("Biscoff Crumbs", "Drink Add-ons", 15.0),
    fixed("Hershey's Drizzle", "Drink Add-ons", 15.0),
    fixed("Crushed Oreo", "Drink Add-ons", 15.0),
    fixed("Drizzle", "Drink Add-ons", 20.0),
    fixed("Syrup", "Drink Add-ons", 15.0),
    fixed("Oatmilk (-)", "Drink Add-ons", 25.0),
    fixed("Breve Milk (-)", "Drink Add-ons", 25.0),
    fixed("Coffee Jelly", "Drink Add-ons", 20.0),
    // Toppings ADD-ONS (as listed in PRICES.docx)
    fixed("Whipped Cream (Food)", "Food Add-ons", 25.0),
    fixed("Alcapone", "Food Add-ons", 15.0),
    fixed("Crushed Oreo (Food)", "Food Add-ons", 15.0),
    fixed("Biscoff Biscuit", "Food Add-ons", 15.0),
    fixed("Biscoff Crumbs (Food)", "Food Add-ons", 15.0),
    fixed("Nutella Drizzle", "Food Add-ons", 20.0),
    fixed("Hershey's Drizzle (Food)", "Food Add-ons", 15.0),
    fixed("Biscoff Drizzle", "Food Add-ons", 20.0),
    fixed("Ice Cream", "Food Add-ons", 25.0),
    // NACHOS ADD-ONS
    fixed("Beef 50g", "Nachos Add-ons", 60.0),
    fixed("Onion", "Nachos Add-ons", 10.0),
    fixed("Tomatoes", "Nachos Add-ons", 10.0),
    fixed("Jalapenos", "Nachos Add-ons", 15.0),
    fixed("Olives", "Nachos Add-ons", 15.0),
    fixed("Cabbage", "Nachos Add-ons", 10.0),
    fixed("Cucumber", "Nachos Add-ons", 10.0),
    fixed("Cheese Sauce", "Nachos Add-ons", 20.0),
    fixed("Garlic Mayo", "Nachos Add-ons", 20.0)
)

private val categories = listOf(
    "Hot Coffee", "Iced Coffee", "Iced Non-Coffee", "Frappe", "Smoothies", "Matcha Series",
    "Croffles", "Waffles", "Pastries", "Nachos", "Fries", "Rice Bowl", "Rice Bowl Add-ons",
    "Drink Add-ons", "Food Add-ons", "Nachos Add-ons"
)

// ============================= MAIN POS =============================
@Composable
fun CafePOSApp() {
    val context = LocalContext.current
    val printerManager = remember { BluetoothPrinterManager(context) }
    var printerConnected by remember { mutableStateOf(printerManager.isConnected()) }
    var printerDialog by remember { mutableStateOf(false) }
    var printerMessage by remember { mutableStateOf("") }

    // Persistent sales and cashier shift records (SQLite, stored on this tablet).
    val shiftDatabase = remember { ShiftDatabase(context) }
    var activeShift by remember { mutableStateOf(shiftDatabase.getActiveShift()) }
    var dailySummary by remember { mutableStateOf(shiftDatabase.getDailySummary(activeShift?.businessDate ?: ShiftDatabase.todayBusinessDate())) }
    var recentShifts by remember { mutableStateOf(shiftDatabase.getRecentShifts()) }
    var recentSales by remember { mutableStateOf(shiftDatabase.getRecentSales()) }
    var shiftDialog by remember { mutableStateOf(false) }
    var shiftMessage by remember { mutableStateOf("") }
    var showSalesDashboard by remember { mutableStateOf(false) }

    fun refreshShiftData() {
        val latestShift = shiftDatabase.getActiveShift()
        activeShift = latestShift
        dailySummary = shiftDatabase.getDailySummary(latestShift?.businessDate ?: ShiftDatabase.todayBusinessDate())
        recentShifts = shiftDatabase.getRecentShifts()
        recentSales = shiftDatabase.getRecentSales()
    }

    var selectedCategory by remember { mutableStateOf("Hot Coffee") }
    var cartItems by remember { mutableStateOf(emptyList<CartItem>()) }
    var orderType by remember { mutableStateOf(OrderType.DINE_IN) }
    var sizeEntry by remember { mutableStateOf<MenuEntry?>(null) }
    var discountItem by remember { mutableStateOf<CartItem?>(null) }
    var checkoutDialog by remember { mutableStateOf(false) }
    var receiptData by remember { mutableStateOf<ReceiptData?>(null) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { printerDialog = true; printerMessage = "" }
        else printerMessage = "Bluetooth permission is required to connect to MTP-II."
    }

    val subtotal = cartItems.sumOf { it.product.price * it.quantity }
    val discountAmount = cartItems.sumOf { calculateItemDiscount(it) }
    val total = (subtotal - discountAmount).coerceAtLeast(0.0)
    val filtered = menuEntries.filter { it.category == selectedCategory }

    if (showSalesDashboard) {
        SalesDashboardScreen(
            printerManager = printerManager,
            onBack = {
                showSalesDashboard = false
                refreshShiftData()
            }
        )
    } else {
        Surface(Modifier.fillMaxSize(), color = PaleBackground) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.catspresso_logo),
                        contentDescription = "CATSPRESSO Logo",
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CATSPRESSO", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                        Text("Cafe POS • Hot and cold sizes separated", fontSize = 11.sp, color = Color.Gray)
                    }
                    OutlinedButton(
                        onClick = { shiftDialog = true; shiftMessage = "" },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            if (activeShift == null) "START SHIFT" else "${activeShift!!.shiftName.uppercase(Locale.US)} • OPEN",
                            fontSize = 10.sp,
                            color = if (activeShift == null) ErrorRed else SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { refreshShiftData(); showSalesDashboard = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("SALES DASHBOARD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else printerDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(if (printerConnected) "MTP-II • CONNECTED" else "PRINTER • OFFLINE", fontSize = 10.sp,
                            color = if (printerConnected) SuccessGreen else ErrorRed, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(Modifier.weight(1.55f).fillMaxHeight(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxSize().padding(10.dp)) {
                            Text("MENU", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                            Spacer(Modifier.height(7.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                categories.forEach { category ->
                                    if (category == selectedCategory) {
                                        Button(onClick = { selectedCategory = category }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                                            Text(category, fontSize = 11.sp)
                                        }
                                    } else {
                                        OutlinedButton(onClick = { selectedCategory = category }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                                            Text(category, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                when (selectedCategory) {
                                    "Hot Coffee" -> "HOT PAPER CUPS • 8oz / 12oz"
                                    "Iced Coffee", "Iced Non-Coffee" -> "COLD CUPS • 12oz / 16oz / 22oz"
                                    "Frappe", "Smoothies", "Matcha Series" -> "COLD CUPS • 12oz / 16oz / 22oz"
                                    else -> "FOOD / ADD-ONS • TAP TO ADD"
                                }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CoffeeAccent
                            )
                            HorizontalDivider(Modifier.padding(vertical = 5.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(filtered.chunked(2)) { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { item ->
                                            val displayPrice = item.fixedPrice?.let { formatPeso(it) }
                                                ?: item.prices.values.minOrNull()?.let { "From ${formatPeso(it)}" }.orEmpty()
                                            Card(
                                                Modifier.weight(1f).clickable {
                                                    if (item.prices.isNotEmpty()) sizeEntry = item
                                                    else {
                                                        val product = Product(
                                                            id = menuEntries.indexOf(item) + 1,
                                                            name = item.name,
                                                            category = item.category,
                                                            price = item.fixedPrice ?: 0.0
                                                        )
                                                        addProductToCart(product, cartItems) { cartItems = it }
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF8))
                                            ) {
                                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                                    Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown, maxLines = 2)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(displayPrice, fontSize = 11.sp, color = CoffeeAccent, fontWeight = FontWeight.SemiBold)
                                                    Text(if (item.prices.isNotEmpty()) "Tap to choose size" else "Tap to add", fontSize = 10.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    Card(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxSize().padding(11.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("CURRENT ORDER", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                                TextButton(onClick = { cartItems = emptyList(); orderType = OrderType.DINE_IN }) { Text("CLEAR", color = ErrorRed) }
                            }
                            HorizontalDivider()
                            Text(
                                "TODAY: ${formatPeso(dailySummary.totalSales)} • ${dailySummary.transactionCount} sale(s)",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoffeeAccent
                            )
                            if (activeShift == null) {
                                Text(
                                    "Start a shift before checkout.",
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    fontSize = 10.sp,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (cartItems.isEmpty()) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("No items yet\nSelect a product from the menu", color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            } else {
                                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(cartItems, key = { it.product.id }) { item ->
                                        OrderItemRow(
                                            cartItem = item,
                                            onIncrease = { cartItems = cartItems.map { if (it.product.id == item.product.id) it.copy(quantity = it.quantity + 1) else it } },
                                            onDecrease = { cartItems = cartItems.mapNotNull { current ->
                                                if (current.product.id != item.product.id) current else if (current.quantity <= 1) null else current.copy(
                                                    quantity = current.quantity - 1,
                                                    discountedQuantity = current.discountedQuantity.coerceAtMost(current.quantity - 1)
                                                )
                                            } },
                                            onRemove = { cartItems = cartItems.filterNot { it.product.id == item.product.id } },
                                            onDiscount = { discountItem = item }
                                        )
                                    }
                                }
                            }
                            Text("ORDER TYPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                SelectionButton("DINE-IN", orderType == OrderType.DINE_IN, Modifier.weight(1f)) { orderType = OrderType.DINE_IN }
                                SelectionButton("TAKE-OUT", orderType == OrderType.TAKE_OUT, Modifier.weight(1f)) { orderType = OrderType.TAKE_OUT }
                            }
                            Spacer(Modifier.height(5.dp))
                            SummaryRow("Subtotal", subtotal)
                            SummaryRow("Discount", discountAmount)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TOTAL", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                                Text(formatPeso(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CoffeeAccent)
                            }
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = { if (activeShift != null) checkoutDialog = true else { shiftDialog = true; shiftMessage = "Start a shift before completing a sale." } }, enabled = cartItems.isNotEmpty() && activeShift != null, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(10.dp)) {
                                Text("CHECKOUT", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

    }

    if (printerDialog) {
        PrinterDialog(
            printerManager = printerManager, connected = printerConnected, message = printerMessage,
            onConnect = { device ->
                val ok = printerManager.connect(device); printerConnected = ok
                printerMessage = if (ok) "MTP-II connected successfully." else "Could not connect to printer."
            },
            onTestPrint = {
                val ok = printerManager.printTestPage(); printerConnected = printerManager.isConnected()
                printerMessage = if (ok) "Test print sent to MTP-II." else "Test print failed."
            },
            onDisconnect = { printerManager.disconnect(); printerConnected = false; printerMessage = "Printer disconnected." },
            onDismiss = { printerDialog = false; printerMessage = "" }
        )
    }

    sizeEntry?.let { item ->
        SizeSelectionDialog(item, onDismiss = { sizeEntry = null }, onSizeSelected = { size, price ->
            val product = Product(menuEntries.indexOf(item) * 100 + size.filter { it.isDigit() }.toInt(), item.name, item.category, size, price)
            addProductToCart(product, cartItems) { cartItems = it }
            sizeEntry = null
        })
    }

    discountItem?.let { item ->
        DiscountItemDialog(item, onDismiss = { discountItem = null }, onSave = { type, quantity, percent ->
            cartItems = cartItems.map { current ->
                if (current.product.id != item.product.id) current else when (type) {
                    DiscountType.SENIOR, DiscountType.PWD -> current.copy(discountedQuantity = quantity, discountType = type, manualDiscountPercent = 0.0)
                    DiscountType.MANUAL -> current.copy(discountedQuantity = 0, discountType = type, manualDiscountPercent = percent)
                    DiscountType.NONE -> current.copy(discountedQuantity = 0, discountType = type, manualDiscountPercent = 0.0)
                }
            }
            discountItem = null
        })
    }

    if (checkoutDialog) {
        CheckoutDialog(total, onDismiss = { checkoutDialog = false }, onComplete = { cash ->
            val currentShift = activeShift
            if (currentShift == null) {
                checkoutDialog = false
                shiftDialog = true
                shiftMessage = "Start a shift before completing a sale."
            } else {
                val now = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.US).format(Date())
                val receipt = ReceiptData(
                    "R-" + System.currentTimeMillis().toString().takeLast(6),
                    cartItems.toList(), subtotal, discountAmount, total, cash, cash - total, orderType, now
                )
                try {
                    // Save first. If saving fails, keep the order in the cart so it can be retried.
                    shiftDatabase.saveSale(receipt, currentShift.id)
                    receiptData = receipt
                    refreshShiftData()
                    if (printerManager.isConnected()) {
                        val printed = printActualReceipt(printerManager, receipt)
                        printerConnected = printerManager.isConnected()
                        printerMessage = if (printed) "Receipt printed successfully." else "Sale saved, but receipt printing failed."
                    } else {
                        printerMessage = "Sale saved. MTP-II is not connected."
                    }
                    cartItems = emptyList()
                    orderType = OrderType.DINE_IN
                    checkoutDialog = false
                    shiftMessage = "Sale ${receipt.receiptNumber} saved successfully."
                } catch (e: Exception) {
                    shiftMessage = "Sale was NOT completed because it could not be saved: ${e.message ?: "database error"}"
                    checkoutDialog = false
                    shiftDialog = true
                }
            }
        })
    }

    if (shiftDialog) {
        ShiftManagementDialog(
            activeShift = activeShift,
            dailySummary = dailySummary,
            recentShifts = recentShifts,
            recentSales = recentSales,
            message = shiftMessage,
            onStartShift = { name, openingCash ->
                try {
                    shiftDatabase.startShift(name, openingCash)
                    refreshShiftData()
                    shiftMessage = "${name.ifBlank { "General Shift" }} started. Previous shifts and sales remain saved."
                } catch (e: Exception) {
                    shiftMessage = e.message ?: "Could not start shift."
                }
            },
            onEndShift = { actualCash ->
                val shift = activeShift
                if (shift == null) {
                    shiftMessage = "There is no active shift to close."
                } else {
                    try {
                        val report = shiftDatabase.closeShift(shift.id, actualCash)
                        refreshShiftData()
                        shiftMessage = "Shift closed. Expected ${formatPeso(report.expectedCash ?: report.computedExpectedCash)}, actual ${formatPeso(actualCash)}, variance ${formatPeso(report.variance ?: 0.0)}."
                    } catch (e: Exception) {
                        shiftMessage = e.message ?: "Could not close shift."
                    }
                }
            },
            onRefresh = { refreshShiftData() },
            onDismiss = { shiftDialog = false }
        )
    }

    receiptData?.let { receipt ->
        ReceiptDialog(
            receipt = receipt,
            printerManager = printerManager,
            onDismiss = { receiptData = null }
        )
    }
}

// ============================= SHIFT / SALES DASHBOARD =============================
@Composable
fun ShiftManagementDialog(
    activeShift: ShiftRecord?,
    dailySummary: DailySalesSummary,
    recentShifts: List<ShiftRecord>,
    recentSales: List<SaleRecord>,
    message: String,
    onStartShift: (String, Double) -> Unit,
    onEndShift: (Double) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var shiftName by remember(activeShift?.id) { mutableStateOf("Morning Shift") }
    var openingCashText by remember(activeShift?.id) { mutableStateOf("0") }
    var actualCashText by remember(activeShift?.id) { mutableStateOf("") }
    val openingCash = openingCashText.toDoubleOrNull()
    val actualCash = actualCashText.toDoubleOrNull()
    val expectedCash = activeShift?.computedExpectedCash ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SALES DASHBOARD & SHIFT CLOSING", fontWeight = FontWeight.Bold, color = CoffeeBrown) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("BUSINESS DATE: ${dailySummary.businessDate}", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                        Column(Modifier.padding(9.dp)) {
                            Text("DAILY SALES", fontSize = 10.sp, color = Color.Gray)
                            Text(formatPeso(dailySummary.totalSales), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                            Text("${dailySummary.transactionCount} transactions", fontSize = 10.sp)
                        }
                    }
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFEBE9))) {
                        Column(Modifier.padding(9.dp)) {
                            Text("DISCOUNTS", fontSize = 10.sp, color = Color.Gray)
                            Text(formatPeso(dailySummary.totalDiscount), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                            Text("${dailySummary.shiftsCount} shift(s)", fontSize = 10.sp)
                        }
                    }
                }
                HorizontalDivider()
                if (activeShift == null) {
                    Text("NO ACTIVE SHIFT", fontWeight = FontWeight.Bold, color = ErrorRed)
                    OutlinedTextField(
                        value = shiftName,
                        onValueChange = { shiftName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Shift name (Morning / Evening)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = openingCashText,
                        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) openingCashText = v },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Opening cash / starting float (₱)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Button(
                        onClick = { onStartShift(shiftName, openingCash!!) },
                        enabled = !openingCashText.isBlank() && openingCash != null && openingCash >= 0.0 && shiftName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("START SHIFT") }
                } else {
                    Text("ACTIVE: ${activeShift.shiftName}", fontWeight = FontWeight.Bold, color = SuccessGreen)
                    Text("Started: ${activeShift.startedAt}", fontSize = 10.sp, color = Color.Gray)
                    SummaryRow("Opening cash", activeShift.openingCash)
                    SummaryRow("Sales in this shift", activeShift.salesTotal)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Transactions", Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                        Text(activeShift.transactionCount.toString(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                    SummaryRow("EXPECTED DRAWER CASH", expectedCash)
                    Text("Count all cash physically in the drawer, including the opening float.", fontSize = 10.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = actualCashText,
                        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) actualCashText = v },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Actual cash counted (₱) — required") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    val variance = if (actualCash != null) actualCash - expectedCash else 0.0
                    if (actualCash != null && actualCash >= 0.0) {
                        SummaryRow("Over / (Short)", variance)
                        Text(
                            when {
                                variance > 0.004 -> "CASH OVER by ${formatPeso(variance)}"
                                variance < -0.004 -> "CASH SHORT by ${formatPeso(-variance)}"
                                else -> "CASH BALANCED"
                            },
                            color = when {
                                variance < -0.004 -> ErrorRed
                                variance > 0.004 -> CoffeeAccent
                                else -> SuccessGreen
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { onEndShift(actualCash!!) },
                        enabled = !actualCashText.isBlank() && actualCash != null && actualCash >= 0.0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) { Text("END SHIFT & SAVE REPORT") }
                }
                if (message.isNotBlank()) {
                    Text(message, fontSize = 11.sp, color = if (message.contains("NOT completed", true) || message.contains("could not", true)) ErrorRed else SuccessGreen)
                }
                HorizontalDivider()
                Text("RECENT SHIFT REPORTS", fontWeight = FontWeight.Bold, color = CoffeeBrown, fontSize = 12.sp)
                if (recentShifts.isEmpty()) Text("No shifts recorded yet.", fontSize = 11.sp, color = Color.Gray)
                recentShifts.take(8).forEach { shift ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${shift.businessDate} • ${shift.shiftName} • ${if (shift.isOpen) "OPEN" else "CLOSED"}", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        Text("Sales ${formatPeso(shift.salesTotal)} • ${shift.transactionCount} sale(s) • Opening ${formatPeso(shift.openingCash)}", fontSize = 10.sp, color = Color.Gray)
                        if (!shift.isOpen) {
                            Text("Expected ${formatPeso(shift.expectedCash ?: shift.computedExpectedCash)} • Actual ${formatPeso(shift.actualCash ?: 0.0)} • Variance ${formatPeso(shift.variance ?: 0.0)}", fontSize = 10.sp, color = if ((shift.variance ?: 0.0) < -0.004) ErrorRed else SuccessGreen)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFECE5E0))
                }
                Text("RECENT TRANSACTIONS", fontWeight = FontWeight.Bold, color = CoffeeBrown, fontSize = 12.sp)
                if (recentSales.isEmpty()) Text("No saved sales yet.", fontSize = 11.sp, color = Color.Gray)
                recentSales.take(10).forEach { sale ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sale.receiptNumber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("${sale.dateTime} • Shift #${sale.shiftId}", fontSize = 9.sp, color = Color.Gray)
                        }
                        Text(formatPeso(sale.total), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("REFRESH REPORTS") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

private fun addProductToCart(product: Product, current: List<CartItem>, update: (List<CartItem>) -> Unit) {
    val existing = current.indexOfFirst { it.product.id == product.id }
    update(if (existing >= 0) current.mapIndexed { index, item -> if (index == existing) item.copy(quantity = item.quantity + 1) else item }
    else current + CartItem(product, 1))
}

// ============================= PRINTER DIALOG =============================
@SuppressLint("MissingPermission")
@Composable
fun PrinterDialog(printerManager: BluetoothPrinterManager, connected: Boolean, message: String,
                  onConnect: (BluetoothDevice) -> Unit, onTestPrint: () -> Unit, onDisconnect: () -> Unit, onDismiss: () -> Unit) {
    val paired = if (connected) emptyList() else printerManager.getPairedDevices()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("THERMAL PRINTER", fontWeight = FontWeight.Bold, color = CoffeeBrown) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(if (connected) "● MTP-II CONNECTED" else "● Printer not connected", color = if (connected) SuccessGreen else ErrorRed, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (connected) {
                    Text("Bluetooth connection is ready.", fontSize = 12.sp, color = Color.Gray)
                    Button(onClick = onTestPrint, modifier = Modifier.fillMaxWidth()) { Text("TEST PRINT") }
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("DISCONNECT") }
                } else {
                    Text("PAIRED BLUETOOTH DEVICES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    if (paired.isEmpty()) Text("No paired devices found. Pair MTP-II in Android Settings first.", fontSize = 11.sp, color = Color.Gray)
                    else Column(Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                        paired.forEach { device -> OutlinedButton(onClick = { onConnect(device) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text(device.name ?: "Unknown device", fontWeight = FontWeight.Bold); Text(device.address, fontSize = 9.sp, color = Color.Gray) }
                        } }
                    }
                }
                if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 8.dp), fontSize = 11.sp, color = if (message.contains("success", true) || message.contains("sent", true)) SuccessGreen else ErrorRed)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } })
}

// ============================= SIZE DIALOG =============================
@Composable
fun SizeSelectionDialog(item: MenuEntry, onDismiss: () -> Unit, onSizeSelected: (String, Double) -> Unit) {
    val sizes = item.prices.entries.toList()
    val cupLabel = when (item.category) {
        "Hot Coffee" -> "HOT PAPER CUP SIZE"
        else -> "COLD CUP SIZE"
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.name, fontWeight = FontWeight.Bold, color = CoffeeBrown) },
        text = {
            Column {
                Text(cupLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CoffeeAccent)
                Spacer(Modifier.height(8.dp))
                sizes.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { sizePrice ->
                            OutlinedButton(onClick = { onSizeSelected(sizePrice.key, sizePrice.value) }, modifier = Modifier.weight(1f)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(sizePrice.key, fontWeight = FontWeight.Bold)
                                    Text(formatPeso(sizePrice.value), fontSize = 11.sp)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}

// ============================= ORDER ROW =============================
@Composable
fun OrderItemRow(cartItem: CartItem, onIncrease: () -> Unit, onDecrease: () -> Unit, onRemove: () -> Unit, onDiscount: () -> Unit) {
    val lineDiscount = calculateItemDiscount(cartItem)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F7F5))) {
        Column(Modifier.fillMaxWidth().padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cartItem.product.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                    Text(listOf(cartItem.product.size, formatPeso(cartItem.product.price)).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 10.sp, color = Color.Gray)
                }
                Text(formatPeso(calculateItemTotal(cartItem)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CoffeeAccent)
            }
            when (cartItem.discountType) {
                DiscountType.SENIOR, DiscountType.PWD -> if (cartItem.discountedQuantity > 0) {
                    val label = if (cartItem.discountType == DiscountType.SENIOR) "Senior 20%" else "PWD 20%"
                    Text("$label • ${cartItem.discountedQuantity} item(s) • -${formatPeso(lineDiscount)}", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                }
                DiscountType.MANUAL -> Text("Manual ${cartItem.manualDiscountPercent}% • -${formatPeso(lineDiscount)}", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                DiscountType.NONE -> Unit
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onDecrease, modifier = Modifier.size(30.dp), contentPadding = PaddingValues(0.dp)) { Text("−") }
                Text("${cartItem.quantity}", Modifier.width(30.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onIncrease, modifier = Modifier.size(30.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDiscount) { Text("DISCOUNT", fontSize = 9.sp, color = if (cartItem.discountType != DiscountType.NONE) SuccessGreen else CoffeeAccent) }
                TextButton(onClick = onRemove) { Text("REMOVE", fontSize = 9.sp, color = ErrorRed) }
            }
        }
    }
}

// ============================= DISCOUNT DIALOG =============================
@Composable
fun DiscountItemDialog(cartItem: CartItem, onDismiss: () -> Unit, onSave: (DiscountType, Int, Double) -> Unit) {
    var selectedType by remember(cartItem.product.id) { mutableStateOf(cartItem.discountType) }
    var discountedQuantity by remember(cartItem.product.id) { mutableIntStateOf(cartItem.discountedQuantity.coerceIn(0, cartItem.quantity)) }
    var percentText by remember(cartItem.product.id) { mutableStateOf(if (cartItem.manualDiscountPercent > 0) cartItem.manualDiscountPercent.toString().removeSuffix(".0") else "") }
    val percent = percentText.toDoubleOrNull()
    val validPercent = percent != null && percent > 0.0 && percent <= 100.0
    val valid = when (selectedType) {
        DiscountType.NONE -> true
        DiscountType.SENIOR, DiscountType.PWD -> discountedQuantity in 1..cartItem.quantity
        DiscountType.MANUAL -> validPercent
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ITEM DISCOUNT", fontWeight = FontWeight.Bold) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(cartItem.product.name, fontWeight = FontWeight.Bold, color = CoffeeBrown)
            Text("${cartItem.product.size} • Qty ${cartItem.quantity}", fontSize = 12.sp, color = Color.Gray)
            SelectionButton("NO DISCOUNT", selectedType == DiscountType.NONE, Modifier.fillMaxWidth()) { selectedType = DiscountType.NONE }
            Text("SENIOR / PWD — 20%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SelectionButton("SENIOR 20%", selectedType == DiscountType.SENIOR, Modifier.weight(1f)) { selectedType = DiscountType.SENIOR }
                SelectionButton("PWD 20%", selectedType == DiscountType.PWD, Modifier.weight(1f)) { selectedType = DiscountType.PWD }
            }
            if (selectedType == DiscountType.SENIOR || selectedType == DiscountType.PWD) {
                Text("Number of eligible items", fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { discountedQuantity = (discountedQuantity - 1).coerceAtLeast(0) }, enabled = discountedQuantity > 0) { Text("−") }
                    Text("$discountedQuantity", Modifier.width(45.dp), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { discountedQuantity = (discountedQuantity + 1).coerceAtMost(cartItem.quantity) }, enabled = discountedQuantity < cartItem.quantity) { Text("+") }
                }
            }
            HorizontalDivider()
            SelectionButton("MANUAL DISCOUNT (%)", selectedType == DiscountType.MANUAL, Modifier.fillMaxWidth()) { selectedType = DiscountType.MANUAL }
            if (selectedType == DiscountType.MANUAL) {
                OutlinedTextField(value = percentText, onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) percentText = value
                }, modifier = Modifier.fillMaxWidth(), label = { Text("Discount percentage (%)") }, placeholder = { Text("Example: 10") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = percentText.isNotBlank() && !validPercent)
                Text("Enter a value above 0 and up to 100. This applies to the full quantity on this line.", fontSize = 10.sp, color = Color.Gray)
                if (validPercent) Text("Discount: ${formatPeso(cartItem.product.price * cartItem.quantity * percent!! / 100.0)}", fontWeight = FontWeight.Bold, color = SuccessGreen)
            }
        }
    }, confirmButton = { Button(enabled = valid, onClick = {
        when (selectedType) {
            DiscountType.NONE -> onSave(DiscountType.NONE, 0, 0.0)
            DiscountType.SENIOR, DiscountType.PWD -> onSave(selectedType, discountedQuantity, 0.0)
            DiscountType.MANUAL -> onSave(DiscountType.MANUAL, 0, percent!!)
        }
    }) { Text("SAVE") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}

// ============================= BUTTON / SUMMARY =============================
@Composable
fun SelectionButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier.heightIn(min = 36.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)) { Text(text, fontSize = 9.sp) }
    else OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 36.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)) { Text(text, fontSize = 9.sp) }
}
@Composable
fun SummaryRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
        Text(formatPeso(amount), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ============================= CHECKOUT =============================
@Composable
fun CheckoutDialog(total: Double, onDismiss: () -> Unit, onComplete: (Double) -> Unit) {
    var cashText by remember { mutableStateOf("") }
    val cash = cashText.toDoubleOrNull() ?: 0.0
    val change = cash - total
    val enough = cash >= total
    AlertDialog(onDismissRequest = onDismiss, title = { Text("PAYMENT", fontWeight = FontWeight.Bold) }, text = {
        Column(Modifier.fillMaxWidth()) {
            Text("TOTAL", fontSize = 12.sp, color = Color.Gray)
            Text(formatPeso(total), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = cashText, onValueChange = { value -> if (value.isEmpty() || value.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) cashText = value }, modifier = Modifier.fillMaxWidth(), label = { Text("Cash Received") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.height(8.dp))
            Text("CHANGE", fontSize = 12.sp, color = Color.Gray)
            Text(if (enough) formatPeso(change) else "Insufficient cash", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = if (enough) SuccessGreen else ErrorRed)
        }
    }, confirmButton = { Button(enabled = enough, onClick = { onComplete(cash) }) { Text("COMPLETE SALE", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}

// ============================= DIGITAL RECEIPT =============================
@Composable
fun ReceiptDialog(
    receipt: ReceiptData,
    printerManager: BluetoothPrinterManager,
    onDismiss: () -> Unit
) {
    var printMessage by remember(receipt.receiptNumber) { mutableStateOf("") }
    val orderLabel = if (receipt.orderType == OrderType.DINE_IN) "DINE-IN" else "TAKE-OUT"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("RECEIPT", fontWeight = FontWeight.Bold, color = CoffeeBrown) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("CATSPRESSO", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
            Text("Coffee & More", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp)
            Text("Stall 2 120 Dona Remedios", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp)
            Text("Trinidad Highway", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp)
            Text("Longos, Pulilan Bulacan 3005", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp)
            Text(orderLabel, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Text("Receipt No.: ${receipt.receiptNumber}", fontSize = 10.sp, color = Color.Gray)
            Text("Status: PAID", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            receipt.items.forEach { item ->
                Text(item.product.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${item.product.size} x ${item.quantity} @ ${formatPeso(item.product.price)}", fontSize = 10.sp, color = Color.Gray)
                when (item.discountType) {
                    DiscountType.SENIOR, DiscountType.PWD -> if (item.discountedQuantity > 0) {
                        val label = if (item.discountType == DiscountType.SENIOR) "Senior 20%" else "PWD 20%"
                        Text("$label (${item.discountedQuantity}) -${formatPeso(calculateItemDiscount(item))}", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                    }
                    DiscountType.MANUAL -> Text("Manual ${item.manualDiscountPercent}% -${formatPeso(calculateItemDiscount(item))}", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                    DiscountType.NONE -> Unit
                }
                Row(Modifier.fillMaxWidth()) { Spacer(Modifier.weight(1f)); Text(formatPeso(calculateItemTotal(item)), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            SummaryRow("Subtotal", receipt.subtotal)
            SummaryRow("Discount", receipt.discountAmount)
            Row(Modifier.fillMaxWidth()) {
                Text("TOTAL", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
                Text(formatPeso(receipt.total), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CoffeeBrown)
            }
            SummaryRow("Cash", receipt.cashReceived)
            SummaryRow("Change", receipt.change)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("THANK YOU!", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CoffeeAccent)
            Text("Please come again.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color.Gray)
            Text(receipt.dateTime, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray)
            if (printMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    printMessage,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = if (printMessage.contains("successfully", ignoreCase = true)) SuccessGreen else ErrorRed
                )
            }
        }
    }, confirmButton = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    printMessage = if (!printerManager.isConnected()) {
                        "Printer is not connected. Connect MTP-II and try again."
                    } else {
                        val printed = printActualReceipt(printerManager, receipt)
                        if (printed) "Additional receipt printed successfully."
                        else "Reprint failed. Check the MTP-II connection."
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("PRINT AGAIN")
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("DONE", fontWeight = FontWeight.Bold)
            }
        }
    })
}

// ============================= THERMAL RECEIPT =============================
fun printActualReceipt(printer: BluetoothPrinterManager, receipt: ReceiptData): Boolean {
    if (!printer.isConnected()) return false
    return try {
        printer.initializePrinter()
        printer.setLineSpacing(24)
        printer.alignCenter(); printer.boldOn(); printer.printText("CATSPRESSO\n"); printer.boldOff()
        printer.printText("Coffee & More\n")
        printer.printText("Stall 2 120 Dona Remedios\n")
        printer.printText("Trinidad Highway\n")
        printer.printText("Longos, Pulilan Bulacan 3005\n")
        printer.printText("--------------------------------\n")
        printer.boldOn(); printer.printText(if (receipt.orderType == OrderType.DINE_IN) "DINE-IN\n" else "TAKE-OUT\n"); printer.boldOff()
        printer.alignLeft(); printer.printText("Receipt No.: ${receipt.receiptNumber}\n")
        printer.printText("${receipt.dateTime}\n")
        printer.printText("--------------------------------\n")
        receipt.items.forEach { item ->
            printer.boldOn(); printer.printText("${item.product.name}\n"); printer.boldOff()
            printer.printText(thermalItemLine("${item.product.size} x ${item.quantity} @ PHP${formatThermalAmount(item.product.price)}", "PHP${formatThermalAmount(calculateItemTotal(item))}"))
            when (item.discountType) {
                DiscountType.SENIOR, DiscountType.PWD -> if (item.discountedQuantity > 0) {
                    val label = if (item.discountType == DiscountType.SENIOR) "Senior 20%" else "PWD 20%"
                    printer.printText(thermalDiscountLine("$label (${item.discountedQuantity})", calculateItemDiscount(item)))
                }
                DiscountType.MANUAL -> printer.printText(thermalDiscountLine("Manual ${item.manualDiscountPercent}%", calculateItemDiscount(item)))
                DiscountType.NONE -> Unit
            }
        }
        printer.printText("--------------------------------\n")
        printer.printText(thermalTotalLine("Subtotal", receipt.subtotal))
        printer.printText(thermalTotalLine("Discount", receipt.discountAmount))
        printer.boldOn(); printer.printText(thermalTotalLine("TOTAL", receipt.total)); printer.boldOff()
        printer.printText(thermalTotalLine("Cash", receipt.cashReceived))
        printer.printText(thermalTotalLine("Change", receipt.change))
        printer.printText("--------------------------------\n")
        printer.alignCenter(); printer.boldOn(); printer.printText("THANK YOU!\n"); printer.boldOff()
        printer.printText("Please come again.\n")
        printer.feed(3); printer.cutPaper(); true
    } catch (_: Exception) { false }
}

fun thermalItemLine(left: String, right: String, width: Int = 32): String {
    val cleanLeft = left.replace("\n", "").trim()
    val cleanRight = right.replace("\n", "").trim()
    val maxLeft = (width - cleanRight.length - 2).coerceAtLeast(1)
    val finalLeft = cleanLeft.take(maxLeft)
    val spaces = (width - finalLeft.length - cleanRight.length).coerceAtLeast(2)
    return finalLeft + " ".repeat(spaces) + cleanRight + "\n"
}
fun thermalDiscountLine(label: String, amount: Double, width: Int = 32): String {
    val right = "-PHP" + formatThermalAmount(amount)
    val finalLabel = label.take((width - right.length - 2).coerceAtLeast(1))
    val spaces = (width - finalLabel.length - right.length).coerceAtLeast(2)
    return finalLabel + " ".repeat(spaces) + right + "\n"
}
fun thermalTotalLine(label: String, amount: Double, width: Int = 32): String {
    val right = "PHP" + formatThermalAmount(amount)
    val spaces = (width - label.length - right.length).coerceAtLeast(1)
    return label + " ".repeat(spaces) + right + "\n"
}