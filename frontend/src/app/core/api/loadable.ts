import { signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';

import { ApiError, toApiError } from './api-error';

/**
 * Loading state of one request for a page: value, loading flag and error. A new load() cancels the previous one,
 * so a slow old answer can never overwrite a newer search.
 * حالة تحميل طلب واحد لصفحة: القيمة، مؤشر التحميل، والخطأ. أي load() جديد يلغي السابق،
 * فالرد القديم البطيء ما يقدر يكتب فوق بحث أحدث.
 */
export class Loadable<T> {
  readonly value = signal<T | null>(null);
  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  private subscription?: Subscription;

  load(source: Observable<T>): void {
    this.subscription?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.subscription = source.subscribe({
      next: (value) => {
        this.value.set(value);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(toApiError(err));
        this.loading.set(false);
      },
    });
  }

  /** Replaces the value without a request, e.g. after an action returned the updated record. / يبدّل القيمة بدون طلب. */
  set(value: T): void {
    this.value.set(value);
  }
}
