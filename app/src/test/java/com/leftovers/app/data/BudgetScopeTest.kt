package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** A Dates budget limited to some categories and/or accounts: only those entries use up its amount. */
class BudgetScopeTest {
    private val food = 1L
    private val transport = 2L
    private val airtime = 3L
    private val rent = 4L
    private val salary = 9L
    private val main = 1L
    private val envelope = 2L

    // Far in the future so the budget is "set" whenever the tests run.
    private val from = LocalDate.of(2099, 1, 1)
    private val to = LocalDate.of(2099, 1, 31)
    private val month = YearMonth.of(2099, 1)

    private fun day(d: Int) = LocalDate.of(2099, 1, d).toEpochDay()

    private fun tx(category: Long, d: Int, amount: Long, account: Long? = main, type: TxType = TxType.EXPENSE, adds: Boolean = false) = TransactionItem(
        id = amount + d, amountMinor = amount, type = type, categoryId = category, epochDay = day(d), note = "", createdAt = 0,
        categoryName = "c$category", categoryEmoji = "tag", categoryColor = 0, accountId = account, receiptPath = null, addsToBudget = adds,
    )

    private fun bill(category: Long, amount: Long, account: Long? = main) = RecurringItem(
        id = category, name = "b$category", amountMinor = amount, type = TxType.EXPENSE, categoryId = category, dayOfMonth = 1,
        startMonth = "2099-01", lastPostedMonth = null, active = true, everyMonths = 1, accountId = account,
        categoryName = "c$category", categoryEmoji = "tag", categoryColor = 0,
    )

    private val plain = BudgetPlan(mode = BudgetMode.RANGE, rangeMinor = 2_000_000, rangeFrom = from, rangeTo = to)
    private val envelopePlan = plain.copy(rangeCategoryIds = setOf(food, transport, airtime))

    // 20,000 set aside for food, transport and airtime; rent is paid in the same dates.
    private val items = listOf(tx(rent, 1, 1_920_000), tx(food, 3, 300_000), tx(transport, 5, 150_000), tx(airtime, 6, 50_000))

    @Test
    fun withoutAScopeRentUsesUpTheBudgetAsBefore() {
        assertFalse(plain.isScoped)
        assertEquals(items, plain.scoped(items))
        assertEquals(0L, plain.leftoverOf(month, items))
    }

    @Test
    fun aCategoryScopeIgnoresRent() {
        assertTrue(envelopePlan.isScoped)
        assertFalse(envelopePlan.inScope(items[0]))
        assertEquals(1_500_000L, envelopePlan.leftoverOf(month, items))
        val today = LocalDate.of(2099, 1, 10)
        val left = dailyBudget(envelopePlan.budgetFor(month, items), envelopePlan.scoped(items), 0, today, to)!!
        assertEquals(1_500_000L, left.monthLeft)
        assertFalse(left.monthUsedUp)
        // The same spending without the scope is over budget.
        val over = dailyBudget(plain.budgetFor(month, items), plain.scoped(items), 0, today, to)!!
        assertTrue(over.monthLeft < 0)
    }

    @Test
    fun anAccountScopeCountsOnlyThatAccount() {
        val byAccount = plain.copy(rangeAccountIds = setOf(envelope))
        val mixed = listOf(tx(food, 2, 100_000, main), tx(food, 2, 200_000, envelope), tx(rent, 2, 900_000, envelope), tx(food, 2, 50_000, null))
        assertEquals(setOf(200_000L, 900_000L), byAccount.scoped(mixed).map { it.amountMinor }.toSet())
    }

    @Test
    fun categoryAndAccountBothHaveToMatch() {
        val both = plain.copy(rangeCategoryIds = setOf(food), rangeAccountIds = setOf(envelope))
        val mixed = listOf(tx(food, 2, 100_000, main), tx(food, 2, 200_000, envelope), tx(rent, 2, 900_000, envelope))
        assertEquals(listOf(200_000L), both.scoped(mixed).map { it.amountMinor })
    }

    @Test
    fun aScopeOnlyAppliesToDatesBudgets() {
        val monthly = BudgetPlan(mode = BudgetMode.MONTHLY, monthlyMinor = 2_000_000, rangeCategoryIds = setOf(food))
        assertFalse(monthly.isScoped)
        assertEquals(items, monthly.scoped(items))
    }

    @Test
    fun extraIncomeFollowsTheAccountChoiceNotTheSpendingCategories() {
        val income = tx(salary, 4, 500_000, envelope, TxType.INCOME, adds = true)
        val onOther = tx(salary, 4, 500_000, main, TxType.INCOME, adds = true)
        val both = plain.copy(rangeCategoryIds = setOf(food), rangeAccountIds = setOf(envelope), extraIncome = true)
        assertEquals(500_000L, both.breakdownFor(month, listOf(income)).income)
        assertEquals(0L, both.breakdownFor(month, listOf(onOther)).income)
        assertEquals(500_000L, envelopePlan.copy(extraIncome = true).breakdownFor(month, listOf(income)).income)
    }

    @Test
    fun subscriptionsOutsideTheScopeAreNotSetAside() {
        val rentBill = bill(rent, 1_920_000)
        val airtimeBill = bill(airtime, 50_000)
        assertEquals(listOf(airtimeBill), envelopePlan.scopedRecurring(listOf(rentBill, airtimeBill)))
        assertEquals(2, plain.scopedRecurring(listOf(rentBill, airtimeBill)).size)
    }

    @Test
    fun savedIdsRoundTripAndHostileValuesAreContained() {
        assertEquals(setOf(1L, 3L, 5L), BudgetPlan.decodeIds(BudgetPlan.encodeIds(setOf(5L, 1L, 3L))))
        assertEquals(setOf(1L, 7L), BudgetPlan.decodeIds("1, x, -4, 0, 7,,"))
        assertTrue(BudgetPlan.decodeIds(null).isEmpty())
        assertTrue(BudgetPlan.decodeIds("").isEmpty())
        assertTrue(BudgetPlan.decodeIds((1..5000).joinToString(",")).size <= BudgetPlan.MAX_SCOPE_IDS)
        assertTrue(BudgetPlan.decodeIds("1,".repeat(500_000)).size <= BudgetPlan.MAX_SCOPE_IDS)
    }
}
