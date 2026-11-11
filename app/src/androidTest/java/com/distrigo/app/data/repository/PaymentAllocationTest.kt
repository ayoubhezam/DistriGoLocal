package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.model.PaymentState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A sale's payment state counts the client's later versements, spent on their sales oldest first (REP-02): each sale's
 * `reste` follows every write that moves the balance, and the filters read it.
 */
@RunWith(AndroidJUnit4::class)
class PaymentAllocationTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() = db.close()

    @Test
    fun aVersementSettlesTheOldestSalesFirst() = runBlocking {
        val milk = (repo.addProduct(mapOf("name" to "Lait", "selling_price" to 100.0, "purchase_price" to 80.0, "stock" to 100.0))["id"] as Number).toInt()
        val client = (repo.addClient(mapOf("name" to "MOSTAFA"))["id"] as Number).toInt()
        // Three sales, none paid: 1 000, then 800, then 500 — with 300 paid on the last
        val first = sale(client, milk, 10.0, 0.0)
        val second = sale(client, milk, 8.0, 0.0)
        val third = sale(client, milk, 5.0, 300.0)
        assertEquals(listOf(1000.0, 800.0, 200.0), reste(first, second, third))
        assertEquals(PaymentState.PARTIAL, state(third))

        // A versement of 1 300: the oldest settled, the middle one partly
        repo.addClientPayment(client, 1300.0, null)
        assertEquals(listOf(0.0, 500.0, 200.0), reste(first, second, third))
        assertEquals(listOf(PaymentState.PAID, PaymentState.PARTIAL, PaymentState.PARTIAL), listOf(state(first), state(second), state(third)))
        assertEquals(1, db.venteDao().countVentesForClient(client, "", "PAYEE"))

        // The rest paid: every sale « Payée », as the ageing says
        repo.addClientPayment(client, 700.0, null)
        assertEquals(listOf(0.0, 0.0, 0.0), reste(first, second, third))
        assertEquals(3, db.venteDao().countVentesForClient(client, "", "PAYEE"))
        assertEquals(0, db.venteDao().countVentesForClient(client, "", "IMPAYEE"))

        // The payment deleted again: the newest owe again first
        val payment = db.clientPaymentDao().getPaymentsForClient(client).maxOf { it.id }
        repo.deleteClientPayment(client, payment)
        assertEquals(listOf(0.0, 500.0, 200.0), reste(first, second, third))
    }

    private suspend fun sale(client: Int, product: Int, quantity: Double, paid: Double): Int {
        repo.createVente(client, null, "depot", listOf(mapOf("product_id" to product, "quantity" to quantity, "unit_price" to 100.0)), null, paid)
        return db.venteDao().getVentesWithDetails(client).maxOf { it.vente.id }
    }

    private suspend fun reste(vararg ids: Int): List<Double> = ids.map { db.venteDao().getVenteById(it)!!.reste }

    private suspend fun state(id: Int): PaymentState = db.venteDao().getVenteById(id)!!.let { PaymentState.ofLeft(it.total, it.reste) }
}
