public final class ClockFixture {
 private static long calls;
 public static synchronized long currentTimeMillis() {
  long initial=Long.getLong("id.clock.millis",1700000000000L);
  long step=Long.getLong("id.clock.step",0L);
  return initial+(calls++)*step;
 }
}