import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, merge, startWith, switchMap } from 'rxjs';
import { ApiError, CollectionOrder } from '../../core/models/payment.models';
import { CollectionOrderApiService } from '../../core/services/collection-order-api.service';
import { PaymentEventsService } from '../../core/services/payment-events.service';
import { ORDER_STATUS_LABEL } from '../../shared/labels';

@Component({
  selector: 'app-orders-panel',
  standalone: true,
  imports: [CurrencyPipe],
  templateUrl: './orders-panel.component.html'
})
export class OrdersPanelComponent {
  private readonly orderApi = inject(CollectionOrderApiService);
  private readonly refresh$ = new Subject<void>();

  readonly orders = signal<CollectionOrder[]>([]);
  readonly error = signal<ApiError | null>(null);
  readonly statusLabel = ORDER_STATUS_LABEL;

  constructor() {
    merge(this.refresh$, inject(PaymentEventsService).processed$)
      .pipe(
        startWith(undefined),
        switchMap(() =>
          this.orderApi.findAll().pipe(
            catchError((err: ApiError) => {
              this.error.set(err);
              return EMPTY;
            })
          )
        ),
        takeUntilDestroyed()
      )
      .subscribe(orders => {
        this.error.set(null);
        this.orders.set(orders);
      });
  }

  refresh(): void {
    this.refresh$.next();
  }
}
