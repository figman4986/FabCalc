package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.ui.AppTool
import com.figapps.fractioncalculatorv1.ui.ContentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The [ContentKey] mapping backs the SaveableStateHolder fix that keeps each tool's and overlay's
 * state alive while off-screen. These tests guard the two properties the fix relies on: every
 * content slot has a unique key, and the tool keys are stable, literal strings.
 */
class SaveableStateKeyTest {

    @Test
    fun everyContentSlotHasAUniqueKey() {
        val keys = ContentKey.all()
        assertEquals("one key per tool plus Settings and History", AppTool.entries.size + 2, keys.size)
        assertEquals("content-slot keys must never collide", keys.size, keys.toSet().size)
    }

    @Test
    fun toolKeysAreStableLiteralsAndDisjointFromOverlayKeys() {
        assertEquals("tool:FRACTION_CALCULATOR", ContentKey.tool(AppTool.FRACTION_CALCULATOR))
        assertEquals("tool:OFFSET_CALCULATOR", ContentKey.tool(AppTool.OFFSET_CALCULATOR))
        assertEquals("tool:CIRCLE_DIVIDER", ContentKey.tool(AppTool.CIRCLE_DIVIDER))
        assertEquals("tool:UNIT_CONVERTER", ContentKey.tool(AppTool.UNIT_CONVERTER))

        AppTool.entries.forEach { tool ->
            assertFalse(ContentKey.tool(tool) == ContentKey.SETTINGS)
            assertFalse(ContentKey.tool(tool) == ContentKey.HISTORY)
        }
    }
}
