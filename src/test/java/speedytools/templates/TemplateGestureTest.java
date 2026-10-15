package speedytools.templates;

import org.junit.Test;
import speedytools.clientside.templates.TemplateGesture;
import static org.junit.Assert.*;

public class TemplateGestureTest
{
  @Test
  public void shortClicksMoveAndPartialHoldsDoNotPlace()
  {
    TemplateGesture gesture = new TemplateGesture();
    gesture.press(10, true); assertEquals(TemplateGesture.Result.SHORT_CLICK, gesture.release(110, 500, 2000));
    gesture.press(10, true); assertEquals(TemplateGesture.Result.NONE, gesture.release(1010, 500, 2000));
  }

  @Test
  public void fullHoldPlacesOnceOnRelease()
  {
    TemplateGesture gesture = new TemplateGesture(); gesture.press(10, true);
    assertTrue(gesture.charging()); assertEquals(2000, gesture.held(2010));
    assertEquals(TemplateGesture.Result.PLACE, gesture.release(2010, 500, 2000));
    assertFalse(gesture.charging()); assertEquals(TemplateGesture.Result.NONE, gesture.release(3010, 500, 2000));
  }

  @Test
  public void previewMustHaveBeenReadyWhenTheHoldStarted()
  {
    TemplateGesture gesture = new TemplateGesture(); gesture.press(10, false);
    assertFalse(gesture.charging()); assertEquals(TemplateGesture.Result.NONE, gesture.release(3010, 500, 2000));
  }

  @Test
  public void CancelOrChangingToolsPreventsPlacementAfterRelease()
  {
    TemplateGesture gesture = new TemplateGesture(); gesture.press(10, true); gesture.cancel();
    assertEquals(TemplateGesture.Result.NONE, gesture.release(3010, 500, 2000));
  }
}
