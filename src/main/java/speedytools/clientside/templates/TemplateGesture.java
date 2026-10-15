package speedytools.clientside.templates;

/** A release can place only when the same ready preview was present at press time. */
public final class TemplateGesture
{
  public enum Result { NONE, SHORT_CLICK, PLACE }
  private long started;
  private boolean down, eligible;
  public void press(long now, boolean ready) { started = now; down = true; eligible = ready; }
  public void cancel() { down = false; eligible = false; }
  public boolean charging() { return down && eligible; }
  public long held(long now) { return down ? Math.max(0, now - started) : 0; }
  public Result release(long now, long shortLimit, long longLimit)
  {
    if (!down) return Result.NONE;
    long duration = held(now); boolean place = eligible && duration >= longLimit;
    cancel(); return place ? Result.PLACE : duration <= shortLimit ? Result.SHORT_CLICK : Result.NONE;
  }
}
