package ir.asudehapp.sms.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneLayoutTest {

    @Test
    fun `phone stays single pane in portrait and landscape`() {
        assertFalse(PaneLayout.isTwoPane(widthDp = 411, heightDp = 891))
        assertFalse(PaneLayout.isTwoPane(widthDp = 891, heightDp = 411))
        assertFalse(PaneLayout.isTwoPane(widthDp = 932, heightDp = 430))
    }

    @Test
    fun `expanded width is two panes`() {
        assertTrue(PaneLayout.isTwoPane(widthDp = 1280, heightDp = 800))
        assertTrue(PaneLayout.isTwoPane(widthDp = 840, heightDp = 1200))
    }

    @Test
    fun `medium width is two panes only in landscape`() {
        assertTrue(PaneLayout.isTwoPane(widthDp = 841, heightDp = 701))
        assertTrue(PaneLayout.isTwoPane(widthDp = 700, heightDp = 600))
        assertFalse(PaneLayout.isTwoPane(widthDp = 673, heightDp = 841))
        assertFalse(PaneLayout.isTwoPane(widthDp = 800, heightDp = 1280))
    }

    @Test
    fun `square medium window stays single pane`() {
        assertFalse(PaneLayout.isTwoPane(widthDp = 700, heightDp = 700))
        assertFalse(PaneLayout.isTwoPane(widthDp = 599, heightDp = 500))
    }
}
