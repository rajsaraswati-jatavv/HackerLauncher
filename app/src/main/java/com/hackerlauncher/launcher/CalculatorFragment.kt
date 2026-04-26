package com.hackerlauncher.launcher

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.widget.GridLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * Scientific Calculator Fragment.
 * Features: basic + scientific operations, hex/oct/bin conversion,
 * calculation history, expression evaluation, keyboard input support.
 */
class CalculatorFragment : Fragment() {

    private lateinit var expressionDisplay: TextView
    private lateinit var resultDisplay: TextView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter

    private var expression = ""
    private var history = mutableListOf<HistoryItem>()
    private var lastResult = ""
    private var currentMode = Mode.DEC // DEC, HEX, OCT, BIN

    enum class Mode(val label: String, val radix: Int) {
        DEC("DEC", 10),
        HEX("HEX", 16),
        OCT("OCT", 8),
        BIN("BIN", 2)
    }

    data class HistoryItem(
        val expression: String,
        val result: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(8, 8, 8, 8)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Mode selector row
        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
        }

        for (mode in Mode.values()) {
            val btn = TextView(context).apply {
                text = " [${mode.label}] "
                setTextColor(
                    if (mode == currentMode) Color.parseColor("#000000")
                    else Color.parseColor("#00FF00")
                )
                setBackgroundColor(
                    if (mode == currentMode) Color.parseColor("#00FF00")
                    else Color.parseColor("#0A0A0A")
                )
                typeface = Typeface.MONOSPACE
                textSize = 12f
                setPadding(8, 4, 8, 4)
                tag = mode
                setOnClickListener { switchMode(mode) }
            }
            modeRow.addView(btn)
        }
        rootLayout.addView(modeRow)

        // Expression display
        expressionDisplay = TextView(context).apply {
            text = "> _"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 24f
            setPadding(16, 16, 16, 4)
            gravity = Gravity.END
            minLines = 2
            setShadowLayer(6f, 0f, 0f, Color.parseColor("#2200FF00"))
        }
        rootLayout.addView(expressionDisplay)

        // Result display
        resultDisplay = TextView(context).apply {
            text = "= 0"
            setTextColor(Color.parseColor("#00AA00"))
            typeface = Typeface.MONOSPACE
            textSize = 32f
            setPadding(16, 4, 16, 16)
            gravity = Gravity.END
            setShadowLayer(8f, 0f, 0f, Color.parseColor("#1100FF00"))
        }
        rootLayout.addView(resultDisplay)

        // Separator
        View(context).apply {
            setBackgroundColor(Color.parseColor("#003300"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply { topMargin = 4; bottomMargin = 4 }
        }.also { rootLayout.addView(it) }

        // Scientific buttons row
        val sciButtons = arrayOf(
            "sin", "cos", "tan", "log",
            "ln", "sqrt", "pow", "n!",
            "pi", "e", "(", ")",
            "HEX", "OCT", "BIN", "DEC"
        )
        val sciGrid = createButtonGrid(sciButtons, 4) { btn ->
            onScientificButton(btn)
        }
        rootLayout.addView(sciGrid)

        // Main buttons grid
        val mainButtons = arrayOf(
            "C", "⌫", "%", "/",
            "7", "8", "9", "*",
            "4", "5", "6", "-",
            "1", "2", "3", "+",
            "0", ".", "^", "="
        )
        val mainGrid = createButtonGrid(mainButtons, 4) { btn ->
            onMainButton(btn)
        }
        rootLayout.addView(mainGrid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        // History
        val historyLabel = TextView(context).apply {
            text = "> history"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(8, 8, 8, 4)
        }
        rootLayout.addView(historyLabel)

        historyRecyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
            setBackgroundColor(Color.parseColor("#050505"))
        }
        historyAdapter = HistoryAdapter(history) { item ->
            expression = item.expression
            lastResult = item.result
            updateDisplay()
        }
        historyRecyclerView.adapter = historyAdapter
        rootLayout.addView(historyRecyclerView)

        return rootLayout
    }

    private fun createButtonGrid(
        buttons: Array<String>,
        columns: Int,
        onClick: (String) -> Unit
    ): GridLayout {
        val context = requireContext()
        return GridLayout(context).apply {
            columnCount = columns
            rowCount = (buttons.size + columns - 1) / columns
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }.also { grid ->
            for (btnLabel in buttons) {
                val btn = TextView(context).apply {
                    text = btnLabel
                    setTextColor(Color.parseColor("#00FF00"))
                    typeface = Typeface.MONOSPACE
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(4, 12, 4, 12)
                    setBackgroundColor(Color.parseColor("#0A1A0A"))
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        height = GridLayout.LayoutParams.WRAP_CONTENT
                        setMargins(2, 2, 2, 2)
                    }
                    setOnClickListener { onClick(btnLabel) }
                }
                grid.addView(btn)
            }
        }
    }

    private fun onMainButton(btn: String) {
        when (btn) {
            "C" -> {
                expression = ""
                lastResult = ""
                updateDisplay()
            }
            "⌫" -> {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                    updateDisplay()
                }
            }
            "=" -> {
                evaluate()
            }
            else -> {
                expression += btn
                updateDisplay()
            }
        }
    }

    private fun onScientificButton(btn: String) {
        when (btn) {
            "sin" -> expression += "sin("
            "cos" -> expression += "cos("
            "tan" -> expression += "tan("
            "log" -> expression += "log("
            "ln" -> expression += "ln("
            "sqrt" -> expression += "sqrt("
            "pow" -> expression += "^"
            "n!" -> expression += "!"
            "pi" -> expression += Math.PI.toString()
            "e" -> expression += Math.E.toString()
            "(" -> expression += "("
            ")" -> expression += ")"
            "HEX" -> switchMode(Mode.HEX)
            "OCT" -> switchMode(Mode.OCT)
            "BIN" -> switchMode(Mode.BIN)
            "DEC" -> switchMode(Mode.DEC)
        }
        updateDisplay()
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        // Refresh mode buttons
        view?.let { v ->
            val modeRow = (v as LinearLayout).getChildAt(0) as LinearLayout
            for (i in 0 until modeRow.childCount) {
                val btn = modeRow.getChildAt(i) as TextView
                val btnMode = btn.tag as? Mode ?: continue
                btn.setTextColor(
                    if (btnMode == currentMode) Color.parseColor("#000000")
                    else Color.parseColor("#00FF00")
                )
                btn.setBackgroundColor(
                    if (btnMode == currentMode) Color.parseColor("#00FF00")
                    else Color.parseColor("#0A0A0A")
                )
            }
        }
        updateDisplay()
    }

    private fun updateDisplay() {
        val displayExpr = if (expression.isEmpty()) "> _" else "> $expression"
        expressionDisplay.text = displayExpr

        // Live preview of result
        if (expression.isNotEmpty()) {
            try {
                val preview = evaluateExpression(expression)
                resultDisplay.text = "= ${formatResult(preview)}"
            } catch (e: Exception) {
                resultDisplay.text = "= ..."
            }
        } else {
            resultDisplay.text = "= 0"
        }
    }

    private fun evaluate() {
        if (expression.isEmpty()) return

        try {
            val result = evaluateExpression(expression)
            val formattedResult = formatResult(result)

            // Add to history
            history.add(
                HistoryItem(
                    expression = expression,
                    result = formattedResult
                )
            )
            if (history.size > 100) history.removeAt(0)
            historyAdapter.notifyItemInserted(history.size - 1)
            historyRecyclerView.scrollToPosition(history.size - 1)

            lastResult = formattedResult
            resultDisplay.text = "= $formattedResult"

            // Set expression to result for chaining
            expression = formattedResult
            expressionDisplay.text = "> $expression"
        } catch (e: Exception) {
            resultDisplay.text = "> error: ${e.message}"
        }
    }

    private fun formatResult(value: Double): String {
        return when (currentMode) {
            Mode.HEX -> {
                if (value == value.toLong().toDouble() && value >= 0) {
                    "0x${value.toLong().toString(16).uppercase()}"
                } else value.toString()
            }
            Mode.OCT -> {
                if (value == value.toLong().toDouble() && value >= 0) {
                    "0o${value.toLong().toString(8)}"
                } else value.toString()
            }
            Mode.BIN -> {
                if (value == value.toLong().toDouble() && value >= 0) {
                    "0b${value.toLong().toString(2)}"
                } else value.toString()
            }
            Mode.DEC -> {
                if (value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    String.format("%.8f", value).trimEnd('0').trimEnd('.')
                }
            }
        }
    }

    /**
     * Manual expression evaluator supporting basic and scientific operations.
     * Uses recursive descent parsing.
     */
    private fun evaluateExpression(expr: String): Double {
        val sanitized = expr
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", Math.PI.toString())
            .trim()

        val parser = ExpressionParser(sanitized)
        return parser.parse()
    }

    /**
     * Recursive descent expression parser.
     */
    private class ExpressionParser(private val input: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseExpression()
            if (pos < input.length) {
                throw IllegalArgumentException("unexpected_char: '${input[pos]}'")
            }
            return result
        }

        private fun parseExpression(): Double {
            var result = parseTerm()
            while (pos < input.length) {
                val c = input[pos]
                when (c) {
                    '+' -> { pos++; result += parseTerm() }
                    '-' -> { pos++; result -= parseTerm() }
                    else -> break
                }
            }
            return result
        }

        private fun parseTerm(): Double {
            var result = parsePower()
            while (pos < input.length) {
                val c = input[pos]
                when (c) {
                    '*' -> { pos++; result *= parsePower() }
                    '/' -> {
                        pos++
                        val divisor = parsePower()
                        if (divisor == 0.0) throw IllegalArgumentException("div_by_zero")
                        result /= divisor
                    }
                    '%' -> { pos++; result %= parsePower() }
                    else -> break
                }
            }
            return result
        }

        private fun parsePower(): Double {
            var result = parseUnary()
            while (pos < input.length && input[pos] == '^') {
                pos++
                val exp = parseUnary()
                result = Math.pow(result, exp)
            }
            return result
        }

        private fun parseUnary(): Double {
            if (pos < input.length && input[pos] == '-') {
                pos++
                return -parseFactor()
            }
            if (pos < input.length && input[pos] == '+') {
                pos++
            }
            return parseFactor()
        }

        private fun parseFactor(): Double {
            skipWhitespace()

            // Functions
            if (pos < input.length) {
                val remaining = input.substring(pos)
                when {
                    remaining.startsWith("sin(") -> {
                        pos += 4
                        val arg = parseExpression()
                        expect(')')
                        return Math.sin(Math.toRadians(arg))
                    }
                    remaining.startsWith("cos(") -> {
                        pos += 4
                        val arg = parseExpression()
                        expect(')')
                        return Math.cos(Math.toRadians(arg))
                    }
                    remaining.startsWith("tan(") -> {
                        pos += 4
                        val arg = parseExpression()
                        expect(')')
                        return Math.tan(Math.toRadians(arg))
                    }
                    remaining.startsWith("log(") -> {
                        pos += 4
                        val arg = parseExpression()
                        expect(')')
                        return Math.log10(arg)
                    }
                    remaining.startsWith("ln(") -> {
                        pos += 3
                        val arg = parseExpression()
                        expect(')')
                        return Math.log(arg)
                    }
                    remaining.startsWith("sqrt(") -> {
                        pos += 5
                        val arg = parseExpression()
                        expect(')')
                        return Math.sqrt(arg)
                    }
                    remaining.startsWith("abs(") -> {
                        pos += 4
                        val arg = parseExpression()
                        expect(')')
                        return Math.abs(arg)
                    }
                }
            }

            // Parentheses
            if (pos < input.length && input[pos] == '(') {
                pos++
                val result = parseExpression()
                expect(')')
                return result
            }

            // Number
            return parseNumber()
        }

        private fun parseNumber(): Double {
            skipWhitespace()
            val start = pos

            // Handle negative numbers within expression
            if (pos < input.length && input[pos] == '-') {
                pos++
            }

            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                pos++
            }

            // Scientific notation
            if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                pos++
                if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
                while (pos < input.length && input[pos].isDigit()) pos++
            }

            if (start == pos) {
                throw IllegalArgumentException("expected_number_at_pos_$pos")
            }

            return input.substring(start, pos).toDouble()
        }

        private fun expect(c: Char) {
            skipWhitespace()
            if (pos < input.length && input[pos] == c) {
                pos++
            } else {
                throw IllegalArgumentException("expected_'$c'_at_pos_$pos")
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }
    }

    /**
     * Factorial calculation.
     */
    private fun factorial(n: Long): Long {
        if (n < 0) throw IllegalArgumentException("negative_factorial")
        if (n > 20) throw IllegalArgumentException("factorial_too_large")
        var result = 1L
        for (i in 2..n) result *= i
        return result
    }

    //region History Adapter

    class HistoryAdapter(
        private val items: List<HistoryItem>,
        private val onClick: (HistoryItem) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        class HistoryViewHolder(itemView: View, val exprView: TextView, val resultView: TextView) :
            RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val context = parent.context
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 6, 12, 6)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            val exprView = TextView(context).apply {
                setTextColor(Color.parseColor("#00AA00"))
                typeface = Typeface.MONOSPACE
                textSize = 12f
            }
            val resultView = TextView(context).apply {
                setTextColor(Color.parseColor("#00FF00"))
                typeface = Typeface.MONOSPACE
                textSize = 14f
            }
            container.addView(exprView)
            container.addView(resultView)
            return HistoryViewHolder(container, exprView, resultView)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = items[position]
            holder.exprView.text = "> ${item.expression}"
            holder.resultView.text = "= ${item.result}"
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    //endregion

    companion object {
        fun newInstance(): CalculatorFragment {
            return CalculatorFragment()
        }
    }
}
