package org.totschnig.myexpenses.viewmodel.data

import android.content.ContentValues
import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.DateTimeFormatter.ISO_LOCAL_DATE
import org.threeten.bp.format.FormatStyle
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.provider.DatabaseConstants.*


data class Budget(val id: Long, val accountId: Long, val title: String, val description: String,
                  val currency: CurrencyUnit, val amount: Money, val grouping: Grouping, val color: Int,
                  val start: LocalDate?, val end: LocalDate?, val accountName: String?) {
    constructor(id: Long, accountId: Long, title: String, description: String, currency: CurrencyUnit, amount: Money, grouping: Grouping, color: Int, start: String?, end: String?, accountName: String?) : this(
            id, accountId, title, description, currency, amount, grouping, color, start?.let { LocalDate.parse(it) }, end?.let { LocalDate.parse(it) }, accountName)

    init {
        when(grouping) {
            Grouping.NONE -> if (start == null || end == null) throw IllegalArgumentException("start and date are required with Grouping.NONE")
            else -> if (start != null || end != null) throw IllegalArgumentException("start and date are only allowed with Grouping.NONE")
        }
    }

    fun toContentValues(): ContentValues {
        val contentValues = ContentValues()
        contentValues.put(KEY_TITLE, title)
        contentValues.put(KEY_DESCRIPTION, description)
        contentValues.put(KEY_GROUPING, grouping.name)
        contentValues.put(KEY_BUDGET, amount.amountMinor)
        if (accountId > 0) {
            contentValues.put(KEY_ACCOUNTID, accountId)
            contentValues.putNull(KEY_CURRENCY)
        } else {
            contentValues.put(KEY_CURRENCY, currency.code())
            contentValues.putNull(KEY_ACCOUNTID)
        }
        if (grouping == Grouping.NONE) {
            contentValues.put(KEY_START, startIso())
            contentValues.put(KEY_END, endIso())
        } else {
            contentValues.putNull(KEY_START)
            contentValues.putNull(KEY_END)
        }
        return contentValues
    }

    fun startIso() = start!!.format(ISO_LOCAL_DATE)
    fun endIso() = end!!.format(ISO_LOCAL_DATE)
    fun durationAsSqlFilter() = "%1\$s > strftime('%%s', '%2\$s', 'utc') AND %1\$s < strftime('%%s', '%3\$s', 'utc')".format(
            KEY_DATE, startIso(), endIso())

    fun durationPrettyPrint(): String {
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        return "%s - %s".format(start!!.format(dateFormat), end!!.format(dateFormat))
    }

    fun titleComplete(context: Context) = "%s (%s)".format(title,
            when(grouping) {
                Grouping.NONE -> durationPrettyPrint()
                else -> context.getString(grouping.getLabelForBudgetType())
            }
    )

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Budget>() {
            override fun areItemsTheSame(oldItem: Budget, newItem: Budget): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Budget, newItem: Budget): Boolean {
                return oldItem == newItem
            }
        }
    }
}

fun Grouping.getLabelForBudgetType() = when (this) {
    Grouping.DAY -> R.string.daily_plain
    Grouping.WEEK -> R.string.weekly_plain
    Grouping.MONTH -> R.string.monthly
    Grouping.YEAR -> R.string.yearly_plain
    Grouping.NONE -> R.string.budget_onetime
}