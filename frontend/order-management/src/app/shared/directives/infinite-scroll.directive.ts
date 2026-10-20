import { Directive, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { fromEvent, Subscription } from 'rxjs';
import { throttleTime } from 'rxjs/operators';

@Directive({
  selector: '[appInfiniteScroll]',
  standalone: true
})
export class InfiniteScrollDirective implements OnInit, OnDestroy {
  private el = inject(ElementRef);
  private scrollSubscription?: Subscription;

  /**
   * Distance from the bottom in pixels to trigger the scroll event.
   * Default is 120 pixels.
   */
  @Input() infiniteScrollDistance: number = 120;

  /**
   * Disables emitting scroll events (e.g., when already fetching a page or when reached the last page).
   */
  @Input() infiniteScrollDisabled: boolean = false;

  /**
   * Throttle time in milliseconds to prevent spamming events.
   */
  @Input() infiniteScrollThrottle: number = 200;

  /**
   * Emitted when user scrolls within `infiniteScrollDistance` of the bottom.
   */
  @Output() appInfiniteScroll = new EventEmitter<void>();

  ngOnInit() {
    const targetElement = this.el.nativeElement as HTMLElement;

    this.scrollSubscription = fromEvent(targetElement, 'scroll', { passive: true })
      .pipe(throttleTime(this.infiniteScrollThrottle, undefined, { leading: true, trailing: true }))
      .subscribe(() => {
        this.checkScrollPosition(targetElement);
      });
  }

  ngOnDestroy() {
    this.scrollSubscription?.unsubscribe();
  }

  private checkScrollPosition(element: HTMLElement) {
    if (this.infiniteScrollDisabled) {
      return;
    }

    const scrollTop = element.scrollTop;
    const scrollHeight = element.scrollHeight;
    const clientHeight = element.clientHeight;

    // Remaining scroll distance to reach bottom
    const distanceToBottom = scrollHeight - (scrollTop + clientHeight);

    if (distanceToBottom <= this.infiniteScrollDistance) {
      this.appInfiniteScroll.emit();
    }
  }
}
