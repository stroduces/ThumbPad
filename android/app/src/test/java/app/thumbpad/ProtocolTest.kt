package app.thumbpad
import org.junit.Assert.*
import org.junit.Test
class ProtocolTest {
    @Test fun compatibilityVector(){assertEquals("545001010100000000000000e8030000000000000000c03f000000c000000000",Packet(1,1,1000,1.5f,-2f).encode().joinToString(""){"%02x".format(it)})}
    @Test fun tapAndDragAreSeparate(){val g=GestureClassifier();g.down(0);g.move(2f,1f);assertEquals(0,g.tap(100));g.down(0);repeat(10){g.move(2f,0f)};assertNull(g.tap(100));g.down(0);assertNull(g.tap(221))}
    @Test fun twoFingerTap(){val g=GestureClassifier();g.down(1);g.move(0f,0f,2);assertEquals(1,g.tap(100))}
    @Test fun rateIndependentTapTravel(){for(samples in listOf(6,9,12)){val g=GestureClassifier();g.down(0);repeat(samples){g.move(12f/samples,0f)};assertNull(g.tap(100))}}
    @Test fun sensitivityIsDeterministic(){assertEquals(pointerDelta(2f,3f,10f,1.4f,.2f,false),pointerDelta(2f,3f,10f,1.4f,.2f,false));assertEquals(.7f,pointerDelta(2f,0f,10f,2f,1f,true).first,.001f);assertTrue(pointerDelta(2f,0f,10f,2f,1f,false).first>2f)}
    @Test fun scrollDirection(){assertEquals(10f,scrollDelta(5f,2f,true));assertEquals(-10f,scrollDelta(5f,2f,false))}
    @Test fun queueBoundedAndButtonsOrdered(){val q=EventQueue(5);assertTrue(q.offer(EventQueue.Event(1,1f,0f,1)));assertTrue(q.offer(EventQueue.Event(1,2f,0f,2)));assertTrue(q.offer(EventQueue.Event(2,0f,1f,3)));assertTrue(q.offer(EventQueue.Event(2,0f,0f,4)));assertEquals(2f,q.poll(10)!!.a);assertEquals(1f,q.poll(10)!!.b);assertEquals(0f,q.poll(10)!!.b);assertNull(q.poll(10));repeat(4){assertTrue(q.offer(EventQueue.Event(2,0f,0f,1)))};assertFalse(q.offer(EventQueue.Event(2,0f,0f,1)))}
    @Test fun staleMotionDroppedButtonsPreserved(){val q=EventQueue();q.offer(EventQueue.Event(1,1f,0f,0));q.offer(EventQueue.Event(2,0f,1f,0));assertEquals(2,q.poll(60_000)!!.kind);q.clear();assertNull(q.poll(60_000))}
}
