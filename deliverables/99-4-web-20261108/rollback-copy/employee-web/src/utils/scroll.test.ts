import { afterEach, describe, expect, it, vi } from 'vitest';
import { scrollElementToBottomAfterLayout } from './scroll';

class FakeScrollElement {
  scrollHeight = 100;
  clientHeight = 100;
  scrollTop = 0;
  calls: Array<{ top: number; behavior?: ScrollBehavior }> = [];

  scrollTo(options: ScrollToOptions) {
    this.calls.push({ top: Number(options.top ?? 0), behavior: options.behavior });
    this.scrollTop = Math.max(0, Number(options.top ?? 0) - this.clientHeight);
  }
}

describe('scroll utilities', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('waits for delayed layout growth before scrolling to the latest message', async () => {
    const element = new FakeScrollElement();
    let frame = 0;
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      frame += 1;
      if (frame === 3) {
        element.scrollHeight = 520;
      }
      callback(frame);
      return frame;
    });

    await scrollElementToBottomAfterLayout(() => element as unknown as HTMLElement, {
      behavior: 'auto',
      maxAttempts: 5,
    });

    expect(element.calls.length).toBeGreaterThanOrEqual(3);
    expect(element.calls[element.calls.length - 1]).toEqual({ top: 520, behavior: 'auto' });
    expect(element.scrollTop).toBe(420);
  });
});
