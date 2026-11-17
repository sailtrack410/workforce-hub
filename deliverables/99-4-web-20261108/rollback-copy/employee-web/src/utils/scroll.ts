export interface ScrollToBottomOptions {
  behavior?: ScrollBehavior;
  maxAttempts?: number;
}

function waitForAnimationFrame() {
  if (typeof requestAnimationFrame !== 'function') return Promise.resolve();
  return new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
}

// 消息列表滚动到底部：多次尝试，等 DOM 渲染完成后再判断是否到达底部
export async function scrollElementToBottomAfterLayout(
  getElement: () => HTMLElement | null | undefined,
  options: ScrollToBottomOptions = {},
) {
  const behavior = options.behavior ?? 'smooth';
  const maxAttempts = options.maxAttempts ?? 12;
  let previousScrollHeight = -1;

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    await waitForAnimationFrame();
    const element = getElement();
    if (!element) continue;

    const target = Math.max(0, element.scrollHeight - element.clientHeight);
    const scrollBehavior = attempt === 0 ? behavior : 'auto';
    element.scrollTo({ top: element.scrollHeight, behavior: scrollBehavior });

    const reachedBottom = Math.abs(element.scrollTop - target) < 4;
    if (target > 0 && reachedBottom && element.scrollHeight === previousScrollHeight) return;
    previousScrollHeight = element.scrollHeight;
  }
}
