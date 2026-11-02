// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DashDesign cannot be loaded on the JVM (its enum builds android.graphics.RectF), so this checks the
 * registry by reading DashDesign.kt itself: ids unique and frozen, defaults valid, slot limits sane.
 */
class DashDesignRegistryTest {
    private val src: String = listOf("src/main/java", "app/src/main/java")
        .map { File(it, "dev/zanderp/opencfmoto/DashDesign.kt") }.first { it.exists() }.readText()

    private class Entry(val name: String, val id: String, val number: Int, val slots: Int, val supported: List<String>, val defaults: List<String>)

    private fun extrasIn(s: String): List<String> = Regex("DashExtra\\.(\\w+)").findAll(s).map { it.groupValues[1] }.toList()

    private fun lists(): Map<String, List<String>> {
        val m = HashMap<String, List<String>>()
        Regex("private val (\\w+) = (listOf\\(.*?\\))(\\s*\\+\\s*(\\w+))?", RegexOption.DOT_MATCHES_ALL).findAll(src).forEach {
            val base = extrasIn(it.groupValues[2])
            m[it.groupValues[1]] = if (it.groupValues[4].isNotEmpty()) base + (m[it.groupValues[4]] ?: error("order")) else base
        }
        return m
    }

    private fun entries(): List<Entry> {
        val l = lists()
        val body = src.substringAfter("enum class DashDesign(").substringAfter(") {").substringBefore("    ;")
        val re = Regex("(\\w+)\\(\\s*\"(\\w+)\",\\s*(\\d+),\\s*R\\.string\\.\\w+,[^\\n]*\\n\\s*(\\d+),\\s*(\\w+),\\s*(listOf\\(.*?\\)|emptyList\\(\\)),\\s*\\{", RegexOption.DOT_MATCHES_ALL)
        return re.findAll(body).map {
            Entry(it.groupValues[1], it.groupValues[2], it.groupValues[3].toInt(), it.groupValues[4].toInt(),
                l[it.groupValues[5]] ?: error("list ${it.groupValues[5]}"), extrasIn(it.groupValues[6]))
        }.toList()
    }

    @Test fun twentyDesigns_numbered1to20_uniqueIds() {
        val e = entries()
        assertEquals(20, e.size)
        assertEquals((1..20).toList(), e.map { it.number })
        assertEquals(20, e.map { it.id }.toSet().size)
    }

    @Test fun storedIdsAreFrozen() {
        val e = entries()
        assertEquals("stock_368", e[0].id)
        assertEquals(listOf("stock_368", "full_map", "arrow_map", "speed_only", "trip_computer", "sport", "night", "touring", "vintage", "info_list"),
            e.take(10).map { it.id })
        assertEquals((11..20).map { "speedo_$it" }, e.drop(10).map { it.id })
        assertTrue(src.contains("val DEFAULT = STOCK_368"))
    }

    @Test fun defaultsAreSupported_withinSlots_noDuplicates() {
        for (d in entries()) {
            assertTrue("${d.name} defaults ⊆ supported", d.supported.containsAll(d.defaults))
            assertTrue("${d.name} defaults <= slots", d.defaults.size <= d.slots)
            assertEquals("${d.name} dup defaults", d.defaults.size, d.defaults.toSet().size)
            assertEquals("${d.name} dup supported", d.supported.size, d.supported.toSet().size)
            assertTrue("${d.name} slots>0", d.slots > 0)
            assertTrue("${d.name} supported >= slots or smaller list", d.supported.size >= minOf(d.slots, d.defaults.size))
        }
    }

    @Test fun pillDesignsStayWithinPillCapacity() {
        // DashKit.cornerPill draws at most MAX_PILL_ITEMS (6) items; designs 2 and 3 use it.
        for (d in entries().filter { it.name == "FULL_MAP" || it.name == "ARROW_MAP" }) assertTrue(d.slots <= 6)
    }

    @Test fun everyRendererFactoryExists() {
        for (d in entries()) {
            val cls = Regex("${d.name}\\(.*?\\{ c, e -> (\\w+)\\(c, e\\) \\}", RegexOption.DOT_MATCHES_ALL).find(src)?.groupValues?.get(1)
            assertTrue("${d.name} factory", cls != null)
            assertTrue("$cls.kt missing", File(File("src/main/java/dev/zanderp/opencfmoto").takeIf { it.exists() } ?: File("app/src/main/java/dev/zanderp/opencfmoto"), "$cls.kt").exists())
        }
    }
}
