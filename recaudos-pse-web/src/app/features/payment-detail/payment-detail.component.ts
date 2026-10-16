import { Component, input } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { PaymentReport } from '../../core/models/payment.models';
import { PAYMENT_STATUS_LABEL } from '../../shared/labels';

@Component({
  selector: 'app-payment-detail',
  standalone: true,
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './payment-detail.component.html'
})
export class PaymentDetailComponent {
  readonly report = input.required<PaymentReport>();
  readonly replayed = input(false);
  readonly title = input('Comprobante de procesamiento');
  readonly statusLabel = PAYMENT_STATUS_LABEL;
}
