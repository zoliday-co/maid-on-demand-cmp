package app.android.mainondemand.domain

/** Whole rupees with Indian digit grouping: 1234567 -> "₹12,34,567". */
fun formatInr(amount: Int): String {
    val digits = amount.toString()
    if (digits.length <= 3) return "₹$digits"
    val groups = digits.dropLast(3).reversed().chunked(2).map { it.reversed() }.reversed()
    return "₹" + groups.joinToString(",") + "," + digits.takeLast(3)
}
