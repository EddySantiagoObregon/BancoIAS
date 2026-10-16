import { Component, computed, inject, signal } from '@angular/core';
import { AsyncPipe, CurrencyPipe, DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, finalize, merge, of, startWith, switchMap } from 'rxjs';
import { ApiError, PaymentReport, PaymentStatus } from '../../core/models/payment.models';
import { PaymentApiService } from '../../core/services/payment-api.service';
import { CollectionOrderApiService } from '../../core/services/collection-order-api.service';
import { PaymentEventsService } from '../../core/services/payment-events.service';
import { PAYMENT_STATUS_LABEL } from '../../shared/labels';

@Component({
  selector: 'app-recent-payments',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, AsyncPipe],
  templateUrl: './recent-payments.component.html'
})
export class RecentPaymentsComponent {
  private readonly paymentApi = inject(PaymentApiService);
  private readonly refresh$ = new Subject<void>();

  readonly limits = [10, 20, 50];
  readonly limit = signal(20);
  readonly orderFilter = signal('');
  readonly orderOptions$ = inject(CollectionOrderApiService).findAll().pipe(catchError(() => of([])));
  readonly reports = signal<PaymentReport[]>([]);
  readonly filter = signal<PaymentStatus | 'ALL'>('ALL');
  readonly visible = computed(() => {
    const f = this.filter();
    return f === 'ALL' ? this.reports() : this.reports().filter(r => r.status === f);
  });
  readonly appliedCount = computed(() => this.reports().filter(r => r.status === 'APPLIED').length);
  readonly rejectedCount = computed(() => this.reports().length - this.appliedCount());
  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  readonly statusLabel = PAYMENT_STATUS_LABEL;

  constructor() {
    // switchMap descarta la respuesta de una consulta anterior si llega otra recarga mientras tanto.
    merge(this.refresh$, inject(PaymentEventsService).processed$)
      .pipe(
        startWith(undefined),
        switchMap(() => {
          this.loading.set(true);
          this.error.set(null);
          return this.paymentApi.findRecent(this.limit(), this.orderFilter()).pipe(
            catchError((err: ApiError) => {
              this.error.set(err);
              return EMPTY;
            }),
            finalize(() => this.loading.set(false))
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe(reports => this.reports.set(reports));
  }

  refresh(): void {
    this.refresh$.next();
  }

  changeLimit(value: string): void {
    this.limit.set(Number(value));
    this.refresh();
  }

  changeOrder(value: string): void {
    this.orderFilter.set(value);
    this.refresh();
  }
}
