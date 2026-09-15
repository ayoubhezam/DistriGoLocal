package com.distrigo.app.data.geo

import android.content.Context
import com.distrigo.app.data.model.Commune
import com.distrigo.app.data.model.Wilaya
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * مصدر بيانات جغرافي ثابت (ولايات الجزائر وبلدياتها)، يُحمَّل مرة واحدة من
 * assets/ إلى الذاكرة. لا حاجة لجدول Room: بيانات مرجعية للقراءة فقط،
 * وwilaya_name/commune_name مخزّنة كنص حر denormalized في الكيانات الحالية.
 */
object GeoRepository {

    @Volatile private var appContext: Context? = null

    // Parsed once, by whichever thread asks first. `lazy` is synchronized: a form that asks while
    // the parse is running waits for it rather than seeing an empty list. MainActivity starts the
    // parse off the main thread straight after setContent; it used to run before setContent, on the
    // main thread, and every cold start paid for it before its first frame.
    private val parsedWilayas: List<Wilaya> by lazy {
        val context = checkNotNull(appContext) { "GeoRepository.init must be called before use" }
        context.assets.open("wilayas.json").bufferedReader().use { reader ->
            val type = object : TypeToken<List<Wilaya>>() {}.type
            Gson().fromJson<List<Wilaya>>(reader, type)
        }
    }

    /** Remembers where to read the file from. Cheap: the file is not opened here. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Parses the file now, on the calling thread, unless it has been already. */
    fun preload() {
        parsedWilayas
    }

    fun getWilayas(): List<Wilaya> = parsedWilayas

    fun getCommunes(wilayaCode: Int): List<Commune> =
        parsedWilayas.find { it.wilayaCode == wilayaCode }?.communes ?: emptyList()

    fun findWilayaByFrName(name: String?): Wilaya? =
        name?.let { n -> parsedWilayas.find { it.nameFr.equals(n, ignoreCase = true) } }

    fun findCommuneByFrName(wilayaCode: Int, name: String?): Commune? =
        name?.let { n -> getCommunes(wilayaCode).find { it.nameFr.equals(n, ignoreCase = true) } }
}