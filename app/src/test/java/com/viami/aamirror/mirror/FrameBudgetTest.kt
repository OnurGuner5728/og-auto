package com.onurguner.ogauto.mirror
import org.junit.Assert.*
import org.junit.Test
class FrameBudgetTest {
 @Test fun skipsOnlyAfterExpensiveDraw() {
   val b = FrameBudget()
   assertTrue(b.canDraw(100))
   b.recordDrawTime(20, 100)
   assertTrue(b.canDraw(101))
   b.recordDrawTime(50, 110)
   assertFalse(b.canDraw(120))
   assertTrue(b.canDraw(130))
 }
}
