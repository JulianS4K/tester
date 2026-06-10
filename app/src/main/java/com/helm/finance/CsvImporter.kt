package com.helm.finance

import com.helm.data.LogEntry
import com.helm.data.LogKind
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses a bank / UPI statement CSV into money [LogEntry]s — no external dependencies.
 *
 * It is tolerant of common export formats: it looks for a header row and maps
 * columns named like date / amount (or debit+credit) / description / category.
 * A positive amount (or a "credit" column) becomes INCOME; otherwise EXPENSE.
 */
object CsvImporter {

    data class Result(val entries: List<LogEntry>, val skipped: Int, val detected: String)

    private val dateFormats = listOf(
        "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy", "dd MMM yyyy", "yyyy/MM/dd",
    ).map { SimpleDateFormat(it, Locale.US) }

    fun parse(text: String, currency: String): Result {
        val rows = text.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (rows.isEmpty()) return Result(emptyList(), 0, "empty file")

        val header = splitCsvLine(rows.first()).map { it.trim().lowercase() }
        val idxDate = header.indexOfFirst { it.contains("date") }
        val idxAmount = header.indexOfFirst { it == "amount" || it.contains("amount") }
        val idxDebit = header.indexOfFirst { it.contains("debit") || it.contains("withdraw") }
        val idxCredit = header.indexOfFirst { it.contains("credit") || it.contains("deposit") }
        val idxDesc = header.indexOfFirst { it.contains("desc") || it.contains("narration") || it.contains("details") || it.contains("particular") }
        val idxCat = header.indexOfFirst { it.contains("category") || it.contains("tag") }

        val hasHeader = idxDate >= 0 || idxAmount >= 0 || idxDebit >= 0 || idxCredit >= 0
        val dataRows = if (hasHeader) rows.drop(1) else rows

        val entries = ArrayList<LogEntry>()
        var skipped = 0
        for (line in dataRows) {
            val cols = splitCsvLine(line)
            val parsed = parseRow(cols, idxDate, idxAmount, idxDebit, idxCredit, idxDesc, idxCat, currency)
            if (parsed != null) entries.add(parsed) else skipped++
        }
        val detected = buildString {
            append(if (hasHeader) "header detected" else "no header")
            if (idxDebit >= 0 || idxCredit >= 0) append(", debit/credit columns")
            else if (idxAmount >= 0) append(", amount column")
        }
        return Result(entries, skipped, detected)
    }

    private fun parseRow(
        cols: List<String>, idxDate: Int, idxAmount: Int, idxDebit: Int, idxCredit: Int,
        idxDesc: Int, idxCat: Int, currency: String,
    ): LogEntry? {
        fun col(i: Int): String? = if (i in cols.indices) cols[i].trim() else null

        val debit = col(idxDebit)?.let { parseAmount(it) }
        val credit = col(idxCredit)?.let { parseAmount(it) }
        val amountCol = col(idxAmount)?.let { parseAmount(it) }

        val (kind, amount) = when {
            credit != null && credit > 0.0 -> LogKind.INCOME to credit
            debit != null && debit > 0.0 -> LogKind.EXPENSE to debit
            amountCol != null -> (if (amountCol >= 0) LogKind.INCOME else LogKind.EXPENSE) to kotlin.math.abs(amountCol)
            else -> return null
        }
        if (amount == 0.0) return null

        val ts = col(idxDate)?.let { parseDate(it) } ?: System.currentTimeMillis()
        val desc = col(idxDesc)?.takeIf { it.isNotBlank() } ?: kind.display
        val category = col(idxCat)?.takeIf { it.isNotBlank() }
        return LogEntry(
            kind = kind, timestamp = ts, title = desc.take(80),
            value = amount, unit = currency, category = category, note = "Imported",
        )
    }

    private fun parseAmount(s: String): Double? {
        val cleaned = s.replace(Regex("[^0-9.\\-]"), "")
        return cleaned.toDoubleOrNull()
    }

    private fun parseDate(s: String): Long? {
        for (f in dateFormats) {
            try { return f.parse(s)?.time } catch (e: Exception) { /* try next */ }
        }
        return null
    }

    /** Minimal CSV splitter that respects double-quoted fields. */
    private fun splitCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
