package com.example.kapsama20

import org.junit.Assert.*
import org.junit.Test

class ConnectionDecisionTest {
    private fun reading(down: Double?, up: Double? = null, ping: Double? = null) =
        RadioReading(null, null, null, "test", mbps = down, upMbps = up, pingMs = ping)

    @Test fun incompleteTestDoesNotPromiseLiveLesson() {
        assertTrue(kararMetni(reading(20.0))!!.contains("eksik"))
        assertTrue(kararMetni(reading(20.0, 2.0))!!.contains("eksik"))
        assertNull(kararMetni(reading(null, 2.0, 30.0)))
    }

    @Test fun uploadAndDelayCanPreventLiveLessonDespiteFastDownload() {
        assertTrue(kararMetni(reading(20.0, 0.2, 30.0))!!.contains("yükleme yetersiz"))
        assertTrue(kararMetni(reading(20.0, 2.0, 400.0))!!.contains("gecikme yüksek"))
        assertTrue(kararMetni(reading(20.0, 2.0, 30.0))!!.contains("tahmini yeterli"))
    }

    @Test fun limitedConnectionsGetAppropriateHomeworkAdvice() {
        assertTrue(kararMetni(reading(2.0))!!.startsWith("Video"))
        assertTrue(kararMetni(reading(0.1))!!.startsWith("Sadece mesaj"))
        assertTrue(kararMetni(reading(0.09))!!.contains("yetersiz"))
    }
}
