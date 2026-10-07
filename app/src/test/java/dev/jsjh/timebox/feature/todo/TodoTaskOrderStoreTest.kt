package dev.jsjh.timebox.feature.todo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodoTaskOrderStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val date = LocalDate.of(2026, 10, 7)
    private val preferences get() = context.getSharedPreferences("todo_task_order_v1", Context.MODE_PRIVATE)

    @Before fun reset() { preferences.edit().clear().commit() }

    @Test fun recreatingStorePreservesOrderedIds() {
        TodoTaskOrderStore(context, "guest").write(date, "brainDump", listOf("C", "A", "B", "C"))
        assertEquals(listOf("C", "A", "B"), TodoTaskOrderStore(context, "guest").read(date, "brainDump"))
    }

    @Test fun unchangedNormalizedOrderSkipsEditingEvenAfterStoreRecreation() {
        var edits = 0
        val counted = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                edits++
                return preferences.edit()
            }
        }
        val countedContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = counted
        }
        val store = TodoTaskOrderStore(countedContext, "guest")
        store.write(date, "brainDump", listOf("A", "B"))
        assertEquals(1, edits)
        store.write(date, "brainDump", listOf("A", "B"))
        store.write(date, "brainDump", listOf("", "A", "B", "A"))
        TodoTaskOrderStore(countedContext, "guest").write(date, "brainDump", listOf("A", "B"))
        assertEquals(1, edits)
        store.write(date, "brainDump", listOf("B", "A"))
        assertEquals(2, edits)
        assertEquals(listOf("B", "A"), store.read(date, "brainDump"))
        store.write(date, "big3", listOf("B", "A"))
        assertEquals(3, edits)
    }

    @Test fun accountDateAndSectionAreIndependent() {
        val guest = TodoTaskOrderStore(context, "guest")
        guest.write(date, "brainDump", listOf("C", "A"))
        guest.write(date, "big3", listOf("B"))
        guest.write(date.plusDays(1), "brainDump", listOf("D"))
        val signedIn = TodoTaskOrderStore(context, "account")
        assertNull(signedIn.read(date, "brainDump"))
        signedIn.write(date, "brainDump", listOf("A", "C"))
        assertEquals(listOf("C", "A"), guest.read(date, "brainDump"))
        assertEquals(listOf("B"), guest.read(date, "big3"))
        assertEquals(listOf("D"), guest.read(date.plusDays(1), "brainDump"))
        assertEquals(listOf("A", "C"), signedIn.read(date, "brainDump"))
    }

    @Test fun malformedMetadataFallsBackWithoutLosingTasks() {
        val key = JSONArray(listOf("guest", date.toString(), "brainDump")).toString()
        val store = TodoTaskOrderStore(context, "guest")
        listOf("not-json", "{}", "[1]", "[\"\"]").forEach { raw ->
            preferences.edit().putString(key, raw).commit()
            assertNull(store.read(date, "brainDump"))
            assertEquals(listOf("A", "B"), reconcileTodoOrder(store.read(date, "brainDump"), listOf("A", "B")))
        }
        store.write(date, "brainDump", listOf("A", "B"))
        assertEquals(listOf("A", "B"), store.read(date, "brainDump"))
    }

    @Test fun metadataWithWrongPreferenceTypeIsIgnored() {
        val key = JSONArray(listOf("guest", date.toString(), "brainDump")).toString()
        preferences.edit().putInt(key, 42).commit()
        val store = TodoTaskOrderStore(context, "guest")
        assertNull(store.read(date, "brainDump"))
        store.write(date, "brainDump", listOf("A", "B"))
        assertEquals(listOf("A", "B"), store.read(date, "brainDump"))
    }

    @Test fun reconciliationRetainsManualOrderAndAppendsNewDiscoveries() {
        assertEquals(listOf("C", "A", "B", "D"), reconcileTodoOrder(
            listOf("C", "deleted", "A", "C", "B"), listOf("A", "B", "C", "D")
        ))
        assertEquals(listOf("A", "B"), reconcileTodoOrder(null, listOf("A", "B")))
    }

    @Test fun visibleDragKeepsHiddenCompletedSlots() {
        assertEquals(listOf("B", "completed", "A"), reorderVisibleTodoTasks(
            listOf("A", "completed", "B"), listOf("A", "B"), "B", 0
        ))
        assertEquals(listOf("completed1", "B", "completed2", "A", "completed3"), reorderVisibleTodoTasks(
            listOf("completed1", "A", "completed2", "B", "completed3"), listOf("A", "B"), "A", 1
        ))
    }

    @Test fun unknownOrHiddenTaskCannotReorderVisibleTasks() {
        val order = listOf("A", "completed", "B")
        assertEquals(order, reorderVisibleTodoTasks(order, listOf("A", "B"), "completed", 0))
        assertEquals(order, reorderVisibleTodoTasks(order, listOf("A", "B"), "missing", 0))
        assertEquals(listOf("B", "completed", "A"), reorderVisibleTodoTasks(order, listOf("A", "B"), "A", 999))
    }
}
